# WebDAV 备份与端到端加密

把 API Key 与设置同步到用户自己的 WebDAV 服务器，**服务器上只有密文**。

## 威胁模型

| 假设 | 对应措施 |
| --- | --- |
| WebDAV 服务器不可信（第三方网盘 / VPS / 被入侵的 NAS） | 客户端加密，服务器只拿到密文 |
| 传输链路可被监听 | 强制 HTTPS；自签名证书需用户显式信任 |
| 设备丢失 | 本机缓存的恢复码与密码用 Android Keystore（TEE）加密 |
| 服务器篡改文件 | AEAD 认证 + 头部参与 AAD |
| 服务器回滚到旧备份 | `revision` 单调递增 + 本地记录已见版本，旧文件直接拒绝 |
| 恶意文件拖死设备 | KDF 参数上限（N ≤ 2²⁰），超限拒绝解密 |

## 候选方案与取舍

当时比较过的四种通用做法：

| | KDF | AEAD | 额外依赖 | 抗离线爆破 | 手表开销 |
| --- | --- | --- | --- | --- | --- |
| A 经典 | PBKDF2-HMAC-SHA256 600k | AES-256-GCM | 无（JCA） | 中 | 0.3–0.8s，无额外内存 |
| **B 内存硬（采用）** | **scrypt N=2¹⁴ r=8 p=1** | **AES-256-GCM** | BouncyCastle（纯 Java） | 高 | 0.5–1.5s，16MB |
| C 现代 | Argon2id m=19MiB t=2 p=1 | XChaCha20-Poly1305 | libsodium/argon2 原生库 | 最高 | 1–2s，19MB |
| D age 格式 | scrypt（age 默认） | ChaCha20-Poly1305 | Android 无成熟库 | 高 | — |

选 **B**：内存硬（显著抬高 GPU 爆破成本），而依赖是纯 Java 的 BouncyCastle，
没有原生库的 ABI 兼容风险；16MB 的单次开销在手表上可以接受，且只在用户手动同步时发生。

C 强度更高，但要引原生库，个别 Wear 设备/模拟器可能加载失败；D 可互通但 Android 侧无可靠实现。

## 密钥从哪来：Keystore 能做什么、不能做什么

Wear OS（含 Galaxy Watch4+ 的 One UI Watch）提供：

- `AndroidKeyStore`（TEE，可选 StrongBox）——密钥不可导出、设备绑定；
- `SecureRandom`——内核 CSPRNG，生成恢复码 / salt / nonce；
- 可选：密钥认证、`setUserAuthenticationRequired`。

**关键限制**：Keystore 里的密钥是设备绑定的，手表 A 加密的文件手表 B 永远解不开。
所以它**不能**当跨设备同步的主密钥。

最终分工：

| 用途 | 用什么 |
| --- | --- |
| 跨设备主密钥材料 | **16 字节随机恢复码**（Crockford Base32，26 字符，128 位熵） |
| 派生实际 AES 密钥 | scrypt(恢复码文本, salt, N=2¹⁴, r=8, p=1) → 32 字节 |
| 本机缓存恢复码 / WebDAV 密码 | Android Keystore AES-GCM（复用 `CryptoBox`） |
| 随机数 | `SecureRandom` |

恢复码按 Crockford 规则归一（大小写不敏感、`I/L→1`、`O→0`、`U→V`、忽略 `-` 与空白），
所以用户抄写时漏个连字符、大小写写错都还能解开；但涉及非法字符会直接判定“格式不对”，
而不是悄悄丢掉字符后报“解不开”。

## 文件格式

服务器上是一个自描述的 JSON 信封，只有 KDF/AEAD 参数是明文：

```json
{
  "format": "apimeter-backup",
  "version": 1,
  "kdf":  { "algo": "scrypt", "n": 16384, "r": 8, "p": 1, "salt": "<base64, 16B>" },
  "aead": { "algo": "AES-256-GCM", "nonce": "<base64, 12B>" },
  "ciphertext": "<base64(ciphertext || 16B tag)>"
}
```

**AAD**（参与 GCM 认证的规范字符串）：

```
apimeter-backup|<version>|scrypt|<n>|<r>|<p>|<salt>|AES-256-GCM|<nonce>
```

改写 iterations、salt、nonce 或 version 任意一项，解密都会失败并提示“恢复码不对，或文件已被篡改”。

解密后的明文负载：

```json
{
  "revision": 3,
  "writtenAt": 1793231623791,
  "writtenBy": "sdk_gwear_x86_64",
  "settings": { "hapticsEnabled": true, "rangePreset": "TODAY", "proxyMode": "AUTO", ... },
  "credentials": [
    { "meta": { "id": "...", "providerKey": "openai", "label": "OpenAI", "updatedAt": 0, "quotaAmount": 0.0 },
      "secrets": { "admin_key": "sk-..." } }
  ]
}
```

只同步**凭据 + 设置**；用量快照是瞬时数据，不传。WebDAV 配置本身也不同步（避免把一台设备的错配置推给另一台）。

## 同步流程与冲突

每次手动同步都是“先拉、再合并、必要时回写”：

1. `MKCOL` 保证目录存在 → `GET` 取密文与 `ETag`（404 视为首次）；
2. 用恢复码解密；解不开就明确报错，**绝不覆盖**服务器文件；
3. 防回滚：远端 `revision` < 本地记录的已见版本 → 中止；
4. 合并：凭据取**并集**（同 id 用 `updatedAt` 新的一方），设置以远端为准；
5. 只有本地真的贡献了新内容才回写，且带 `If-Match: <ETag>`；
   若期间被其他设备抢先写（412），提示“服务器上的备份已被其他设备更新”。

**已知语义**：删除不会同步——一台设备删掉的凭据会被并集“复活”。
对密钥备份来说这是有意的取舍（宁可多留一条，也不要把用户唯一的那份 Key 弄丢）。

## 传输安全

- 默认只接受 `https://`；`http://` 必须在界面上显式打开「允许 http（不安全）」，
  界面上会显示红色警告。
- 自签名证书默认不信任，需显式打开「信任自签名证书」。
- manifest 里的 `android:usesCleartextTraffic="true"` 只为了让上述**用户显式选择**的
  http 选项能真正生效（Android 从 API 28 起默认在平台层拦截明文 HTTP，
  应用内校验管不到平台策略）。厂商用量接口一律走 https，不受影响。

## 验证情况

单测（JVM）：

- `BackupCryptoTest` 14 项：含 **RFC 7914 两条 scrypt 官方向量**、往返、
  恢复码容错、以及盐/迭代/密文/超限 KDF 被篡改时必须失败；
- `BackupMergeTest` 8 项：并集、新旧取舍、设置以远端为准、revision 取最大、删除不传播；
- `WebDavClientTest` 10 项（MockWebServer）：ETag 头、`If-None-Match: *`、
  412 → 冲突、401 → 认证失败、MKCOL 405 容忍、https 强制；
- `BackupEndToEndTest` 3 项：真实端口上跑完 MKCOL → PUT → GET → 解密 → 合并，
  并断言服务器文件里不含明文密钥。

真机（Wear OS 模拟器 + 本地假 WebDAV）：

- 界面配置 → 生成恢复码 → 保存并同步 → 服务端日志 `MKCOL / GET 404 / PUT`，界面回显
  “已创建备份（v1，2 个凭据）”；
- 二次同步走合并路径，回显“已从服务器拉取（v1，2 个凭据）”，未产生多余写入；
- 服务器上的文件用 **Node 标准 `crypto`** 独立解密成功，内容与设备上一致
  —— 说明格式是通用可读的，不依赖本应用的实现细节。

## 尚未做的

- 常驻后台同步：现在只有「启动时同步」（App 启动后跑一次拉取 + 合并），
  没有 WorkManager 周期备份；日常靠「保存并同步」手动触发。
- 手机端 Companion 传恢复码（换设备目前要手抄 26 个字符）。
- 恢复码轮换/重置（改了会导致已有备份解不开，故意不提供入口）。
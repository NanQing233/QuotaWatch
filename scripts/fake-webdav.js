#!/usr/bin/env node
/**
 * 最小 WebDAV 服务器：只为验证本项目的备份同步（本地跑，不用于生产）。
 *
 * 支持：Basic 认证、MKCOL、GET、PUT（含 If-Match / If-None-Match 乐观并发）。
 * 数据放在内存里，重启即清空——正好用来观察“首次创建 / 冲突 / 合并”这三种路径。
 *
 * 用法：node scripts/fake-webdav.js [port] [user] [pass]
 */
const http = require('http');
const crypto = require('crypto');

const port = Number(process.argv[2] || 8099);
const user = process.argv[3] || 'tester';
const pass = process.argv[4] || 'secret';

/** path -> { body, etag, updatedAt } */
const files = new Map();
/** 已创建过的目录（用于让 MKCOL 返回 405） */
const collections = new Set();

function etagOf(body) {
  return '"' + crypto.createHash('sha256').update(body).digest('hex').slice(0, 16) + '"';
}

function unauth(res) {
  res.writeHead(401, { 'WWW-Authenticate': 'Basic realm="fake-webdav"' });
  res.end('unauthorized');
}

function authorized(req) {
  const header = req.headers['authorization'] || '';
  if (!header.startsWith('Basic ')) return false;
  const decoded = Buffer.from(header.slice(6), 'base64').toString('utf8');
  return decoded === `${user}:${pass}`;
}

function readBody(req) {
  return new Promise((resolve) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
  });
}

const server = http.createServer(async (req, res) => {
  if (!authorized(req)) return unauth(res);

  const path = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  console.log(`${req.method} ${path}`);

  if (req.method === 'MKCOL') {
    if (collections.has(path)) {
      res.writeHead(405).end('already exists');
    } else {
      collections.add(path);
      // 顺带记录上级目录
      let parent = path.replace(/\/[^/]+\/?$/, '');
      while (parent) {
        collections.add(parent.endsWith('/') ? parent : parent + '/');
        const next = parent.replace(/\/[^/]+\/?$/, '');
        if (next === parent) break;
        parent = next;
      }
      res.writeHead(201).end();
    }
    return;
  }

  if (req.method === 'GET') {
    const file = files.get(path);
    if (!file) return res.writeHead(404).end('not found');
    res.writeHead(200, {
      'Content-Type': 'application/json; charset=utf-8',
      ETag: file.etag,
    });
    return res.end(file.body);
  }

  if (req.method === 'PUT') {
    const ifMatch = req.headers['if-match'];
    const ifNoneMatch = req.headers['if-none-match'];
    const existing = files.get(path);

    if (ifNoneMatch === '*' && existing) {
      return res.writeHead(412).end('precondition failed');
    }
    if (ifMatch && (!existing || existing.etag !== ifMatch)) {
      return res.writeHead(412).end('precondition failed');
    }

    const body = await readBody(req);
    const etag = etagOf(body);
    files.set(path, { body, etag, updatedAt: Date.now() });
    res.writeHead(existing ? 204 : 201, { ETag: etag }).end();
    return;
  }

  if (req.method === 'DELETE') {
    files.delete(path);
    return res.writeHead(204).end();
  }

  res.writeHead(405).end('method not allowed');
});

server.listen(port, '0.0.0.0', () => {
  console.log(`fake-webdav listening on http://0.0.0.0:${port} as ${user}:${pass}`);
  console.log(`file url: http://10.0.2.2:${port}/dav/apimeter/backup.json (from emulator)`);
});
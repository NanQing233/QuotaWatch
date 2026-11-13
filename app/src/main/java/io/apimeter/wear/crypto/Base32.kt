package io.apimeter.wear.crypto

/** Crockford Base32：字母表去掉 I/L/O/U，避免小屏抄写时与 1/0 混淆；解码时做等价映射。 */
internal object Base32Crockford {

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val builder = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                builder.append(ALPHABET[(buffer ushr bits) and 0x1F])
            }
        }
        if (bits > 0) {
            builder.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        }
        return builder.toString()
    }

    /** 格式不对返回 null；非法字符不静默丢弃，直接判错，便于用户区分「抄错」和「解不开」。 */
    fun decode(text: String): ByteArray? {
        for (ch in text) {
            if (!isAllowed(ch)) return null
        }
        val cleaned = normalize(text)
        if (cleaned.isEmpty()) return null
        val out = ByteArray(cleaned.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        for (ch in cleaned) {
            val value = ALPHABET.indexOf(ch)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out[index++] = ((buffer ushr bits) and 0xFF).toByte()
            }
        }
        return out
    }

    private fun isAllowed(ch: Char): Boolean =
        ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z' ||
            ch.isWhitespace() || ch == '-' || ch == '_' || ch == '.'

    /** 去分隔符与空白，并按 Crockford 规则归一易混字符。 */
    fun normalize(text: String): String = buildString {
        for (raw in text.uppercase()) {
            when {
                raw.isWhitespace() || raw == '-' || raw == '_' || raw == '.' -> Unit
                raw == 'I' || raw == 'L' -> append('1')
                raw == 'O' -> append('0')
                raw == 'U' -> append('V')
                else -> append(raw)
            }
        }
    }

    /** 每 4 位加一个连字符，方便抄写；解码前会被 [normalize] 去掉。 */
    fun group(text: String): String = text.chunked(4).joinToString("-")
}

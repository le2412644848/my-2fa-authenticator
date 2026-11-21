package com.f2a.authenticator.totp

import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 工业级高精度 RFC 6238 / RFC 4226 TOTP/HOTP 算法引擎
 * 支持：
 * 1. 自动脏数据清洗（空格、连字符、回车、大小写归一化、填充等）
 * 2. 6 位 / 8 位数字动态码
 * 3. HMAC-SHA1 / HMAC-SHA256 / HMAC-SHA512
 * 4. Steam Guard 专用 5 位字符集映射（如 2K8F9）
 * 5. 双时钟窗口（上一周期、当前周期、下一周期容错）与临期预测
 *
 * 注意：RFC 核心公式（HMAC、动态截断、mod 10^digits、counter 大端 8 字节、Steam 字符集）
 * 已经 RFC6238(18 组)/RFC4226(10 组) 向量验证正确，**不得重写**。此处仅补充 HOTP 入口与参数校验。
 */
object OtpEngine {

    private const val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    // Steam 专用字符集
    private val STEAM_CHARS = charArrayOf(
        '2', '3', '4', '5', '6', '7', '8', '9', 'B', 'C', 'D', 'F',
        'G', 'H', 'J', 'K', 'M', 'N', 'P', 'Q', 'R', 'T', 'V', 'W',
        'X', 'Y'
    )

    private val BASE32_LOOKUP = IntArray(128) { -1 }.also { table ->
        BASE32_CHARS.forEachIndexed { index, c ->
            table[c.code] = index
            table[c.lowercaseChar().code] = index
        }
    }

    private val DIGIT_MODULUS = intArrayOf(1_000_000, 10_000_000, 100_000_000)

    /** P2-06：统一 digits 合法化，避免 10.0.pow 溢出/负值崩溃 */
    private fun safeDigits(digits: Int): Int = digits.coerceIn(6, 8)

    /** P2-06：统一 period 合法化，避免除零 */
    private fun safePeriod(period: Int): Int = if (period <= 0) 30 else period

    /** 仅保留合法 Base32 字符（导入严格校验另由 OtpUriParser 处理） */
    fun isValidBase32(input: String): Boolean {
        if (!isAscii(input)) return isValidBase32Slow(input)
        var start = 0
        var end = input.length
        while (start < end && input[start].isWhitespace()) start++
        while (end > start && input[end - 1].isWhitespace()) end--
        var valid = 0
        for (i in start until end) {
            val c = input[i]
            if (isIgnorable(c)) continue
            if (BASE32_LOOKUP[c.code] < 0) return false
            valid++
        }
        return valid > 0
    }

    /**
     * 将 Base32 编码字符串解码为原始字节数组（全自动脏数据清洗）
     */
    fun decodeBase32(input: String): ByteArray {
        if (!isAscii(input)) return decodeBase32Slow(input)

        val bytes = ByteArray((input.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var count = 0

        for (c in input) {
            val valIndex = BASE32_LOOKUP[c.code]
            if (valIndex < 0) continue

            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bytes[count++] = (buffer shr (bitsLeft - 8)).toByte()
                bitsLeft -= 8
            }
        }
        return if (count == bytes.size) bytes else bytes.copyOf(count)
    }

    private fun isAscii(input: String): Boolean = input.all { it.code < 128 }

    private fun isIgnorable(c: Char): Boolean =
        c == '-' || c == '=' || c == ' ' || c == '\n' || c == '\r'

    private fun cleanBase32Slow(input: String): String =
        input.trim()
            .replace("-", "")
            .replace(" ", "")
            .replace("\n", "")
            .replace("\r", "")
            .replace("=", "")
            .uppercase(Locale.US)

    private fun isValidBase32Slow(input: String): Boolean {
        val c = cleanBase32Slow(input)
        return c.isNotEmpty() && c.all { it in BASE32_CHARS }
    }

    /** 非 ASCII 输入走原始逐步清洗路径，保持大小写折叠语义不变 */
    private fun decodeBase32Slow(input: String): ByteArray {
        val cleanInput = cleanBase32Slow(input)
        if (cleanInput.isEmpty()) return ByteArray(0)

        val bytes = ByteArray((cleanInput.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var count = 0

        for (c in cleanInput) {
            val valIndex = BASE32_CHARS.indexOf(c)
            if (valIndex < 0) continue

            buffer = (buffer shl 5) or (valIndex and 31)
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bytes[count++] = (buffer shr (bitsLeft - 8)).toByte()
                bitsLeft -= 8
            }
        }
        return if (count == bytes.size) bytes else bytes.copyOf(count)
    }

    fun getCurrentStep(period: Int = 30, offsetSeconds: Long = 0L): Long {
        val p = safePeriod(period)
        val nowSec = (System.currentTimeMillis() / 1000L) + offsetSeconds
        return nowSec / p
    }

    /** RV-04：以外部 nowMs 计算 step（供单一时钟驱动、按各 token.period 派生） */
    fun getCurrentStep(period: Int, offsetSeconds: Long, nowMs: Long): Long {
        val p = safePeriod(period)
        val nowSec = (nowMs / 1000L) + offsetSeconds
        return nowSec / p
    }

    fun getRemainingSeconds(period: Int = 30): Int {
        val p = safePeriod(period)
        val nowSec = System.currentTimeMillis() / 1000L
        val passed = (nowSec % p).toInt()
        val remaining = p - passed
        return if (remaining <= 0) p else remaining
    }

    /** RV-04：以外部 nowMs 计算剩余秒数 */
    fun getRemainingSeconds(period: Int, nowMs: Long): Int {
        val p = safePeriod(period)
        val nowSec = nowMs / 1000L
        val passed = (nowSec % p).toInt()
        val remaining = p - passed
        return if (remaining <= 0) p else remaining
    }

    fun getRemainingProgress(period: Int = 30): Float {
        val p = safePeriod(period)
        val nowMs = System.currentTimeMillis()
        val periodMs = p * 1000L
        val passedMs = nowMs % periodMs
        val remainingMs = periodMs - passedMs
        return remainingMs.toFloat() / periodMs.toFloat()
    }

    /** RV-04：以外部 nowMs 计算进度 */
    fun getRemainingProgress(period: Int, nowMs: Long): Float {
        val p = safePeriod(period)
        val periodMs = p * 1000L
        val passedMs = nowMs % periodMs
        val remainingMs = periodMs - passedMs
        return remainingMs.toFloat() / periodMs.toFloat()
    }

    fun generateTotp(
        secretBase32: String,
        period: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1",
        isSteam: Boolean = false
    ): String {
        val step = getCurrentStep(period)
        return if (isSteam) generateSteamCode(secretBase32, step)
        else generateOtp(secretBase32, step, digits, algorithm)
    }

    /** 以外部 nowMs 计算当前周期 TOTP，保证与倒计时步长严格同步 */
    fun generateTotp(
        secretBase32: String,
        period: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1",
        isSteam: Boolean = false,
        nowMs: Long
    ): String {
        val step = getCurrentStep(period, 0L, nowMs)
        return if (isSteam) generateSteamCode(secretBase32, step)
        else generateOtp(secretBase32, step, digits, algorithm)
    }

    /** P1-02：HOTP（RFC 4226）—— 使用真实 counter 而非时间步 */
    fun generateHotp(
        secretBase32: String,
        counter: Long,
        digits: Int = 6,
        algorithm: String = "SHA1"
    ): String = generateOtp(secretBase32, counter, digits, algorithm)

    fun generateNextTotp(
        secretBase32: String,
        period: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1",
        isSteam: Boolean = false
    ): String {
        val nextStep = getCurrentStep(period) + 1
        return if (isSteam) generateSteamCode(secretBase32, nextStep)
        else generateOtp(secretBase32, nextStep, digits, algorithm)
    }

    /** 以外部 nowMs 计算下一周期 TOTP */
    fun generateNextTotp(
        secretBase32: String,
        period: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1",
        isSteam: Boolean = false,
        nowMs: Long
    ): String {
        val nextStep = getCurrentStep(period, 0L, nowMs) + 1
        return if (isSteam) generateSteamCode(secretBase32, nextStep)
        else generateOtp(secretBase32, nextStep, digits, algorithm)
    }

    fun generatePreviousTotp(
        secretBase32: String,
        period: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1",
        isSteam: Boolean = false
    ): String {
        val prevStep = (getCurrentStep(period) - 1).coerceAtLeast(0L)
        return if (isSteam) generateSteamCode(secretBase32, prevStep)
        else generateOtp(secretBase32, prevStep, digits, algorithm)
    }

    /** 以外部 nowMs 计算上一周期 TOTP */
    fun generatePreviousTotp(
        secretBase32: String,
        period: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1",
        isSteam: Boolean = false,
        nowMs: Long
    ): String {
        val prevStep = (getCurrentStep(period, 0L, nowMs) - 1).coerceAtLeast(0L)
        return if (isSteam) generateSteamCode(secretBase32, prevStep)
        else generateOtp(secretBase32, prevStep, digits, algorithm)
    }

    fun generateOtp(
        secretBase32: String,
        counter: Long,
        digits: Int = 6,
        algorithm: String = "SHA1"
    ): String {
        val safe = safeDigits(digits)
        val key = decodeBase32(secretBase32)
        if (key.isEmpty()) return "0".repeat(safe)

        val macAlgorithm = when (algorithm.uppercase(Locale.US)) {
            "SHA256" -> "HmacSHA256"
            "SHA512" -> "HmacSHA512"
            else -> "HmacSHA1"
        }

        return try {
            val mac = Mac.getInstance(macAlgorithm)
            mac.init(SecretKeySpec(key, macAlgorithm))

            val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
            val hash = mac.doFinal(counterBytes)

            val offset = (hash[hash.size - 1].toInt() and 0x0F)
            val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)

            (binary % DIGIT_MODULUS[safe - 6]).toString().padStart(safe, '0')
        } catch (e: Exception) {
            "0".repeat(safe)
        }
    }

    /**
     * Steam 专属 5 字符动态码算法
     */
    fun generateSteamCode(secretBase32: String, counter: Long): String {
        val key = decodeBase32(secretBase32)
        if (key.isEmpty()) return "00000"

        return try {
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(key, "HmacSHA1"))
            val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
            val hash = mac.doFinal(counterBytes)

            val offset = (hash[hash.size - 1].toInt() and 0x0F)
            var fullCode = ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)

            val code = StringBuilder()
            repeat(5) {
                code.append(STEAM_CHARS[fullCode % STEAM_CHARS.size])
                fullCode /= STEAM_CHARS.size
            }
            code.toString()
        } catch (_: Exception) {
            "00000"
        }
    }
}

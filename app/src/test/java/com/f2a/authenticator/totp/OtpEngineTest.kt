package com.f2a.authenticator.totp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class OtpEngineTest {

    private val seedSha1Base32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    private val seedSha256Base32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA===="
    private val seedSha512Base32 =
        "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA="

    @Test
    fun rfc4226_all10HotpVectors_matchStandard() {
        val expected = listOf(
            "755224", "287082", "359152", "969429", "338314",
            "254676", "287922", "162583", "399871", "520489"
        )
        expected.forEachIndexed { counter, code ->
            val actual = OtpEngine.generateHotp(
                secretBase32 = seedSha1Base32,
                counter = counter.toLong(),
                digits = 6,
                algorithm = "SHA1"
            )
            assertEquals("HOTP counter=$counter", code, actual)
        }
    }

    @Test
    fun rfc6238_all18TotpVectors_sha1Sha256Sha512_matchStandard() {
        val sha1Cases = listOf(
            59L to "94287082",
            1111111109L to "07081804",
            1111111111L to "14050471",
            1234567890L to "89005924",
            2000000000L to "69279037",
            20000000000L to "65353130"
        )
        val sha256Cases = listOf(
            59L to "46119246",
            1111111109L to "68084774",
            1111111111L to "67062674",
            1234567890L to "91819424",
            2000000000L to "90698825",
            20000000000L to "77737706"
        )
        val sha512Cases = listOf(
            59L to "90693936",
            1111111109L to "25091201",
            1111111111L to "99943326",
            1234567890L to "93441116",
            2000000000L to "38618901",
            20000000000L to "47863826"
        )

        sha1Cases.forEach { (timeSec, expected) ->
            val actual = OtpEngine.generateTotp(
                secretBase32 = seedSha1Base32,
                period = 30,
                digits = 8,
                algorithm = "SHA1",
                isSteam = false,
                nowMs = timeSec * 1000L
            )
            assertEquals("SHA1 t=$timeSec", expected, actual)
        }
        sha256Cases.forEach { (timeSec, expected) ->
            val actual = OtpEngine.generateTotp(
                secretBase32 = seedSha256Base32,
                period = 30,
                digits = 8,
                algorithm = "SHA256",
                isSteam = false,
                nowMs = timeSec * 1000L
            )
            assertEquals("SHA256 t=$timeSec", expected, actual)
        }
        sha512Cases.forEach { (timeSec, expected) ->
            val actual = OtpEngine.generateTotp(
                secretBase32 = seedSha512Base32,
                period = 30,
                digits = 8,
                algorithm = "SHA512",
                isSteam = false,
                nowMs = timeSec * 1000L
            )
            assertEquals("SHA512 t=$timeSec", expected, actual)
        }
    }

    @Test
    fun steamCode_uses5CharSteamAlphabet() {
        val code = OtpEngine.generateSteamCode(seedSha1Base32, counter = 1L)
        assertEquals("PV9M4", code)
        assertEquals(5, code.length)

        val totpSteam = OtpEngine.generateTotp(
            secretBase32 = seedSha1Base32,
            period = 30,
            digits = 5,
            algorithm = "SHA1",
            isSteam = true,
            nowMs = 30_000L
        )
        assertEquals("PV9M4", totpSteam)
    }

    @Test
    fun nowMsOverloads_areSynchronizedAtPeriodBoundaries() {
        val beforeBoundaryMs = 29_999L
        val atBoundaryMs = 30_000L

        val prevAtBoundary = OtpEngine.generatePreviousTotp(
            secretBase32 = seedSha1Base32,
            period = 30,
            digits = 6,
            algorithm = "SHA1",
            isSteam = false,
            nowMs = atBoundaryMs
        )
        val currBeforeBoundary = OtpEngine.generateTotp(
            secretBase32 = seedSha1Base32,
            period = 30,
            digits = 6,
            algorithm = "SHA1",
            isSteam = false,
            nowMs = beforeBoundaryMs
        )
        val currAtBoundary = OtpEngine.generateTotp(
            secretBase32 = seedSha1Base32,
            period = 30,
            digits = 6,
            algorithm = "SHA1",
            isSteam = false,
            nowMs = atBoundaryMs
        )
        val nextBeforeBoundary = OtpEngine.generateNextTotp(
            secretBase32 = seedSha1Base32,
            period = 30,
            digits = 6,
            algorithm = "SHA1",
            isSteam = false,
            nowMs = beforeBoundaryMs
        )

        assertEquals(currBeforeBoundary, prevAtBoundary)
        assertEquals(currAtBoundary, nextBeforeBoundary)
        assertEquals(1, OtpEngine.getRemainingSeconds(30, beforeBoundaryMs))
        assertEquals(30, OtpEngine.getRemainingSeconds(30, atBoundaryMs))
    }

    @Test
    fun base32Validation_handlesDirtyAndTurkishLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertTrue(OtpEngine.isValidBase32("jbsw-y3dp ehpk 3pxp"))
            assertTrue(OtpEngine.isValidBase32("iiii2222"))
            assertFalse(OtpEngine.isValidBase32("invalid!0189"))
            assertFalse(OtpEngine.isValidBase32("   "))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun base32Decode_ignoresSeparatorsCaseAndPadding() {
        val expected = "Hello!\u00de\u00ad\u00be\u00ef".toByteArray(Charsets.ISO_8859_1)
        assertArrayEquals(expected, OtpEngine.decodeBase32("JBSWY3DPEHPK3PXP"))
        assertArrayEquals(expected, OtpEngine.decodeBase32(" jbsw-y3dp ehpk\n3pxp==== "))
        assertArrayEquals(expected, OtpEngine.decodeBase32("JBSW!Y3DP0EHPK3PXP"))
        assertArrayEquals("f".toByteArray(), OtpEngine.decodeBase32("MY======"))
        assertArrayEquals("foobar".toByteArray(), OtpEngine.decodeBase32("MZXW6YTBOI======"))
        assertEquals(0, OtpEngine.decodeBase32("").size)
        assertEquals(0, OtpEngine.decodeBase32("-= \n").size)
    }

    @Test
    fun base32Validation_onlyTrimsOuterWhitespace() {
        assertTrue(OtpEngine.isValidBase32("\tJBSWY3DPEHPK3PXP\t"))
        assertFalse(OtpEngine.isValidBase32("JBSW\tY3DP"))
        assertFalse(OtpEngine.isValidBase32("-=-"))
        assertFalse(OtpEngine.isValidBase32("JBSWY3DP\u00e9"))
    }

    @Test
    fun otp_keepsLeadingZerosFor6And8Digits() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val codes = (0L until 2000L).map { OtpEngine.generateHotp(secret, it, 6) }
        assertTrue(codes.all { it.length == 6 && it.all(Char::isDigit) })
        assertTrue(codes.any { it.startsWith("0") })
        assertTrue((0L until 200L).all { OtpEngine.generateHotp(secret, it, 8).length == 8 })
    }
}

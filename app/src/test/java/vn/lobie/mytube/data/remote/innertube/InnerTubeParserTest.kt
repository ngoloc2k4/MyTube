package vn.lobie.mytube.data.remote.innertube

import org.junit.Assert.assertEquals
import org.junit.Test

class InnerTubeParserTest {

    @Test
    fun testParseViewCount_emptyAndZero() {
        assertEquals(0L, InnerTubeParser.parseViewCount(""))
        assertEquals(0L, InnerTubeParser.parseViewCount("   "))
        assertEquals(0L, InnerTubeParser.parseViewCount("0 views"))
        assertEquals(0L, InnerTubeParser.parseViewCount("0 lượt xem"))
    }

    @Test
    fun testParseViewCount_rawNumbers() {
        assertEquals(1L, InnerTubeParser.parseViewCount("1 view"))
        assertEquals(999L, InnerTubeParser.parseViewCount("999 views"))
        assertEquals(12345L, InnerTubeParser.parseViewCount("12,345 views"))
        assertEquals(12345L, InnerTubeParser.parseViewCount("12.345 lượt xem"))
    }

    @Test
    fun testParseViewCount_vietnameseFormats() {
        // Thousands ("N", "nghìn")
        assertEquals(58_000L, InnerTubeParser.parseViewCount("58 N lượt xem"))
        assertEquals(58_600L, InnerTubeParser.parseViewCount("58,6 N lượt xem"))
        assertEquals(58_600L, InnerTubeParser.parseViewCount("58.6 N lượt xem"))
        assertEquals(10_000L, InnerTubeParser.parseViewCount("10 nghìn lượt xem"))

        // Millions ("Tr", "triệu")
        assertEquals(1_200_000L, InnerTubeParser.parseViewCount("1,2 Tr lượt xem"))
        assertEquals(58_600_000L, InnerTubeParser.parseViewCount("58,6 Tr lượt xem"))
        assertEquals(58_600_000L, InnerTubeParser.parseViewCount("58.6 Tr lượt xem"))
        assertEquals(3_000_000L, InnerTubeParser.parseViewCount("3 triệu lượt xem"))

        // Billions ("Tỷ")
        assertEquals(1_200_000_000L, InnerTubeParser.parseViewCount("1,2 Tỷ lượt xem"))
        assertEquals(1_000_000_000L, InnerTubeParser.parseViewCount("1 tỷ lượt xem"))
    }

    @Test
    fun testParseViewCount_englishFormats() {
        // Thousands ("K")
        assertEquals(58_000L, InnerTubeParser.parseViewCount("58K views"))
        assertEquals(58_600L, InnerTubeParser.parseViewCount("58.6K views"))
        assertEquals(10_000L, InnerTubeParser.parseViewCount("10K views"))

        // Millions ("M")
        assertEquals(1_200_000L, InnerTubeParser.parseViewCount("1.2M views"))
        assertEquals(58_600_000L, InnerTubeParser.parseViewCount("58.6M views"))

        // Billions ("B")
        assertEquals(1_200_000_000L, InnerTubeParser.parseViewCount("1.2B views"))
    }

    @Test
    fun testParseViewCount_regressionBugCheck() {
        // In the buggy version, "58 N lượt xem" matched 'M' from "lượt xem" and became 58M (58,000,000)
        // With the fix, it must be 58,000.
        val result = InnerTubeParser.parseViewCount("58 N lượt xem")
        assertEquals(58_000L, result)

        val resultDecimal = InnerTubeParser.parseViewCount("58,6 N lượt xem")
        assertEquals(58_600L, resultDecimal)
    }
}

package com.harambee.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    @Test
    fun money() {
        assertEquals(150_050L, Money.parseToCents("1,500.50"))
        assertEquals(200_000L, Money.parseToCents("Ksh 2,000"))
        assertEquals(null, Money.parseToCents("abc"))
        assertEquals("1,000", Money.format(100_000))
        assertEquals("1,234,567.05", Money.format(123_456_705))
    }

    @Test
    fun phone() {
        assertEquals("0712345678", Phone.normalize("+254 712 345 678"))
        assertEquals("0712345678", Phone.normalize("712345678"))
        assertEquals("0723 934 660", Phone.pretty("254723934660"))
        assertEquals(Phone.contributorKey("A", "0712345678"), Phone.contributorKey("B", "254712345678"))
    }

    @Test
    fun nameMatching() {
        assertTrue(Names.matches("CO Peter chesos", "Peter Kiprono Chesos"))
        assertTrue(Names.matches("Silas chepsoi", "SILAS CHEPSOI"))
        assertFalse(Names.matches("Faith Kwambai", "Faith Rutto"))
        assertFalse(Names.matches("Noah", "Noah Kiptoo"))
    }
}

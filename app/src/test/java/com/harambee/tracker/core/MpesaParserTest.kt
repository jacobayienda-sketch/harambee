package com.harambee.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MpesaParserTest {
    private fun received(body: String) = (MpesaParser.parse(body) as MpesaMessage.Received).receipt

    @Test
    fun parsesStandardReceivedMessage() {
        val r = received(
            "SJ12ABC3DE Confirmed.You have received Ksh1,500.00 from JANE WANJIKU 0712345678 on 2/10/26 at 3:45 PM  " +
                "New M-PESA balance is Ksh12,345.00. Separate personal and business funds through Pochi la Biashara on *334#.",
        )
        assertEquals("SJ12ABC3DE", r.code)
        assertEquals(150_000L, r.amountCents)
        assertEquals("Jane Wanjiku", r.senderName)
        assertEquals("0712345678", r.senderPhone)
        assertEquals(Instant.parse("2026-10-02T12:45:00Z").toEpochMilli(), r.transactionTime)
    }

    @Test
    fun parsesMaskedInternationalNumber() {
        val r = received("TJ4AB12XYZ Confirmed. You have received Ksh500.00 from ELIUD KIPCHUMBA MURKOMEN 254723***660 on 30/9/26 at 10:02 AM New M-PESA balance is Ksh3,000.00.")
        assertEquals("Eliud Kipchumba Murkomen", r.senderName)
        assertEquals("0723***660", r.senderPhone)
        assertEquals(50_000L, r.amountCents)
    }

    @Test
    fun parsesBankOrPaybillSender() {
        val r = received("TJ5CD34EFG Confirmed.You have received Ksh10,000.00 from Equity Bulk Account 300600 on 1/10/26 at 5:01 PM New M-PESA balance is Ksh13,000.00.")
        assertEquals("Equity Bulk Account", r.senderName)
        assertEquals("300600", r.senderPhone)
    }

    @Test
    fun parsesRealSampleWithoutPhone() {
        val r = received("UJ1C68SPRQ Confirmed. You have received Ksh2,000.00 from MTRH RSPO on 1/10/26 at 3:33 PM. New M-PESA balance is Ksh5,297.82. Separate personal and business funds through Pochi la Biashara on *334#.")
        assertEquals("UJ1C68SPRQ", r.code)
        assertEquals(200_000L, r.amountCents)
        assertEquals("Mtrh Rspo", r.senderName)
        assertNull(r.senderPhone)
        assertEquals(Instant.parse("2026-10-01T12:33:00Z").toEpochMilli(), r.transactionTime)
    }

    @Test
    fun parsesRealSampleWithMaskedPhoneAndDoubleSpace() {
        val r = received("UIUAB8L07F Confirmed.You have received Ksh1,000.00 from SANDRA  SIRMA 0723***873 on 30/9/26 at 3:45 PM  New M-PESA balance is Ksh6,103.82. Invest & earn daily interest with ZIIDI on https://saf.cx/cF6ir")
        assertEquals("UIUAB8L07F", r.code)
        assertEquals(100_000L, r.amountCents)
        assertEquals("Sandra Sirma", r.senderName)
        assertEquals("0723***873", r.senderPhone)
        assertEquals(Instant.parse("2026-09-30T12:45:00Z").toEpochMilli(), r.transactionTime)
    }

    @Test
    fun parsesSwahiliMessage() {
        val r = received("TJ6GH56IJK Imethibitishwa. Umepokea Ksh2,000.00 kutoka kwa PETER CHESOS 0722111222 mnamo 1/10/26 saa 8:15 PM. Salio jipya la M-PESA ni Ksh5,000.00.")
        assertEquals("TJ6GH56IJK", r.code)
        assertEquals(200_000L, r.amountCents)
        assertEquals("Peter Chesos", r.senderName)
        assertNotNull(r.transactionTime)
    }

    @Test
    fun ignoresSentPaymentsAndAirtime() {
        assertEquals(MpesaMessage.NotRelevant, MpesaParser.parse("SJ12ABC3DE Confirmed. Ksh1,000.00 sent to JOHN DOE 0712345678 on 2/10/26 at 3:45 PM. New M-PESA balance is Ksh500.00."))
        assertEquals(MpesaMessage.NotRelevant, MpesaParser.parse("SJ12ABC3DE confirmed.You bought Ksh50.00 of airtime on 2/10/26 at 3:45 PM."))
        assertEquals(MpesaMessage.NotRelevant, MpesaParser.parse("Hello, how are you?"))
    }

    @Test
    fun detectsReversal() {
        val msg = MpesaParser.parse("TJ7KL78MNO Confirmed. Transaction SJ12ABC3DE has been reversed. Your account balance is now Ksh500.00.")
        assertEquals(MpesaMessage.Reversal("SJ12ABC3DE"), msg)
    }

    @Test
    fun splitsSeveralPastedMessages() {
        val text = """
            SJ12ABC3DE Confirmed.You have received Ksh1,000.00 from A B 0711111111 on 2/10/26 at 3:45 PM New M-PESA balance is Ksh1.00.
            SJ12ABC3DF Confirmed.You have received Ksh500.00 from C D 0722222222 on 2/10/26 at 3:46 PM New M-PESA balance is Ksh2.00.
        """.trimIndent()
        val parts = MpesaParser.splitMessages(text)
        assertEquals(2, parts.size)
        assertTrue(parts.all { MpesaParser.parse(it) is MpesaMessage.Received })
    }

    @Test
    fun unreadableDateFallsBackToNull() {
        assertNull(MpesaParser.parseDateTime("31/2/26", "3:45 PM"))
    }
}

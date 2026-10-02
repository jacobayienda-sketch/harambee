package com.harambee.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsAndPrivacyTest {
    private val payTo = listOf(PayTo("Eliud Murkomen", "0723934660"))

    @Test
    fun privacyNameStyles() {
        assertEquals("Jane Wanjiku", NameDisplay.apply("Jane Wanjiku", NameDisplay.FULL))
        assertEquals("Jane K.", NameDisplay.apply("Jane Wanjiku Kamau", NameDisplay.FIRST_INITIAL))
        assertEquals("J.W.K.", NameDisplay.apply("Jane Wanjiku Kamau", NameDisplay.INITIALS))
        assertEquals("CO Peter C.", NameDisplay.apply("CO Peter chesos", NameDisplay.FIRST_INITIAL))
        assertEquals("Mtrh", NameDisplay.apply("Mtrh", NameDisplay.FIRST_INITIAL))
        assertEquals("Well-wisher", NameDisplay.apply("Jane Wanjiku", NameDisplay.HIDDEN))
        assertEquals("Well-wisher", NameDisplay.apply("Jane Wanjiku", NameDisplay.FULL, anonymous = true))
    }

    @Test
    fun milestones() {
        assertEquals(50, Milestones.crossed(4_900_000, 5_100_000, 10_000_000))
        assertEquals(75, Milestones.crossed(2_000_000, 8_000_000, 10_000_000)) // jumped two: report the highest
        assertNull(Milestones.crossed(5_100_000, 5_200_000, 10_000_000))
        assertEquals(100, Milestones.crossed(9_900_000, 10_000_000, 10_000_000))
        assertNull(Milestones.crossed(0, 100, null))
        assertEquals(25, Milestones.reached(3_000_000, 10_000_000))
        assertNull(Milestones.reached(1_000_000, 10_000_000))
    }

    @Test
    fun singleEntry() {
        val text = UpdateFormats.single("Burial", "Jane W.", 100_000, true, Tally(4_550_000, 46, 10_000_000), payTo)
        assertEquals(
            """
            ✅ *Received with thanks*
            Jane W. — KES 1,000
            Asante sana 🙏

            *Burial*
            *Total received: KES 45,500*
            Contributors: 46
            Target: KES 100,000 (45%)
            Balance: KES 54,500
            Send your contribution to *Eliud Murkomen 0723 934 660*
            """.trimIndent(),
            text,
        )
        assertFalse(UpdateFormats.single("Burial", "Jane W.", 100_000, false, Tally(0, 0, null), emptyList()).contains("1,000"))
    }

    @Test
    fun batchKeepsFullListNumbers() {
        val lines = listOf(
            UpdateLine("a", "Eliud", 100_000, true, 1),
            UpdateLine("b", "Hillary", 100_000, true, 2),
            UpdateLine("c", "Sandra", 100_000, true, 3, isNew = true),
            UpdateLine("d", "John", 100_000, false, 4),
            UpdateLine("e", "Mtrh Rspo", 200_000, true, 5, isNew = true),
        )
        val text = UpdateFormats.batch("Burial", lines, UpdateOptions(), Tally(600_000, 5, null), emptyList())
        assertTrue(text.contains("_Since the last update: 2 people, KES 3,000_"))
        assertTrue(text.contains("3. Sandra 1,000 ✅"))
        assertTrue(text.contains("5. Mtrh Rspo 2,000 ✅"))
        assertFalse(text.contains("Eliud"))
        assertTrue(UpdateFormats.batch("Burial", lines.map { it.copy(isNew = false) }, UpdateOptions(), Tally(0, 0, null), emptyList()).contains("No new contributions"))
    }

    @Test
    fun milestoneMessage() {
        val text = UpdateFormats.milestone("Burial", 50, Tally(5_000_000, 48, 10_000_000), payTo)
        assertTrue(text.startsWith("*Halfway there! 🎉*"))
        assertTrue(text.contains("*Burial* has reached *50%* of the target."))
        assertTrue(text.contains("KES 50,000 of KES 100,000 raised by 48 contributors."))
        assertTrue(text.contains("Balance: *KES 50,000*"))
    }

    @Test
    fun hidesAmountsInList() {
        val text = WhatsAppUpdateBuilder.build(
            UpdateContent("", emptyList(), "", null),
            listOf(UpdateLine("a", "Jane W.", 100_000, true, 1)),
            UpdateOptions(showAmounts = false, addNextNumber = false),
            0,
        )
        assertTrue(text.contains("1. Jane W. ✅"))
        assertTrue(text.contains("*Total received: KES 1,000*"))
    }

    @Test
    fun publicPageEscapesAndRespectsPrivacy() {
        val html = PublicPage.html(
            PublicPageData(
                name = "Mama's <Fund>", intro = "", payTo = payTo, tally = Tally(150_000, 2, 1_000_000),
                lines = listOf(UpdateLine("a", "Well-wisher", 100_000, true, 1), UpdateLine("b", "J.W.", 50_000, true, 2), UpdateLine("c", "P", 9, false, 3)),
                showAmounts = false, updatedAt = 0,
            ),
        )
        assertTrue(html.contains("Mama's &lt;Fund&gt;"))
        assertFalse(html.contains("<Fund>"))
        assertTrue(html.contains("Well-wisher"))
        assertFalse(html.contains("class=\"amt\""))
        assertTrue(html.contains("15%"))
        assertFalse(html.contains("0723934660")) // pretty-printed only
    }

    @Test
    fun contributorsCsv() {
        val csv = ContributorsCsv.build(listOf(ContributorsCsv.Person("Doe, Jane", "0712345678", 150_000, 50_000, 2, null, true)))
        assertEquals("No,Name,Phone,Paid (KES),Pledged unpaid (KES),Payments,Last payment,Anonymous\n1,\"Doe, Jane\",0712345678,1500,500,2,,yes\n", csv)
    }
}

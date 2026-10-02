package com.harambee.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppListTest {
    private val post = """
        Good Morning colleagues. Following the demise of Sister to our colleague Agnes Jemutai Kwambai(Accountant Sports), the family has decided to do a fundraising on Wednesday 30th September. Let us kindly send Our generous contribution to  *Eliud Murkomen  0723934660*

             *Contribution List*
        1. Eliud Murkomen 1,000 ✅
        2. Hillary Chebii 1,000 ✅
        23. Oliver Kimutai 500 ✅
        38. John cheruyot 1,000
        41. CO Peter chesos 3,000 ✅
        63. Jerono Tanui  500 ✅
        70. Johnkeen Jairo 2,500 ✅
        72. 
        Thanks for your generous contribution 🙏
    """.trimIndent()

    @Test
    fun parsesExistingWhatsAppPost() {
        val list = WhatsAppListParser.parse(post)
        assertEquals(7, list.entries.size)
        assertEquals(ListEntry("CO Peter chesos", 300_000, true), list.entries[4])
        assertEquals(ListEntry("John cheruyot", 100_000, false), list.entries[3])
        assertEquals(ListEntry("Jerono Tanui", 50_000, true), list.entries[5])
        assertTrue(list.intro.startsWith("Good Morning colleagues"))
        assertFalse(list.intro.contains("Contribution List"))
        assertEquals("Thanks for your generous contribution 🙏", list.footer)
    }

    @Test
    fun lineVariants() {
        assertEquals(ListEntry("Mama Njeri", 200_000, true), WhatsAppListParser.parseLine("Mama Njeri - Ksh 2,000/= paid"))
        assertEquals(ListEntry("Josh", 50_000, false), WhatsAppListParser.parseLine("Josh 500"))
        assertEquals(null, WhatsAppListParser.parseLine(""))
    }

    @Test
    fun buildsUpdateInTheGroupsFormat() {
        val text = WhatsAppUpdateBuilder.build(
            UpdateContent("Good Morning colleagues.", listOf(PayTo("Eliud Murkomen", "0723934660")), "Thanks for your generous contribution 🙏", 10_000_000),
            listOf(
                UpdateLine("e", "Eliud Murkomen", 100_000, true, 1),
                UpdateLine("j", "John cheruyot", 100_000, false, 3),
                UpdateLine("h", "Hillary Chebii", 150_000, true, 2),
            ),
            UpdateOptions(),
            0,
        )
        val expected = """
            Good Morning colleagues.

            Send your contribution to *Eliud Murkomen 0723 934 660*

                 *Contribution List*
            1. Eliud Murkomen 1,000 ✅
            2. Hillary Chebii 1,500 ✅
            3. John cheruyot 1,000
            4. 

            *Total received: KES 2,500*
            Pledges pending: KES 1,000
            Target: KES 100,000 (2%)
            Balance: KES 97,500

            Thanks for your generous contribution 🙏
        """.trimIndent()
        assertEquals(expected, text)
    }

    @Test
    fun doesNotRepeatNumberAlreadyInIntro() {
        val text = WhatsAppUpdateBuilder.build(
            UpdateContent("Send to *Eliud Murkomen 0723934660*", listOf(PayTo("Eliud Murkomen", "0723934660")), "", null),
            emptyList(), UpdateOptions(showTotal = false), 0,
        )
        assertFalse(text.contains("Send your contribution"))
    }

    private val repeatLines = listOf(
        UpdateLine("tel:0711", "Oliver Kimutai", 50_000, true, 1),
        UpdateLine("tel:0722", "Nancy Mosop", 50_000, true, 2),
        UpdateLine("tel:0711", "Oliver Kimutai", 50_000, true, 3),
    )

    @Test
    fun combinesRepeatPayersIntoOneLine() {
        val text = WhatsAppUpdateBuilder.build(UpdateContent("", emptyList(), "", null), repeatLines, UpdateOptions(showTotal = false, addNextNumber = false), 0)
        assertEquals("     *Contribution List*\n1. Oliver Kimutai 1,000 ✅\n2. Nancy Mosop 500 ✅", text)
    }

    @Test
    fun canKeepRepeatPaymentsSeparate() {
        val text = WhatsAppUpdateBuilder.build(UpdateContent("", emptyList(), "", null), repeatLines, UpdateOptions(showTotal = false, addNextNumber = false, combineRepeat = false), 0)
        assertTrue(text.contains("3. Oliver Kimutai 500 ✅"))
    }

    @Test
    fun latestOnlyKeepsRealNumbers() {
        val lines = (1..30).map { UpdateLine("k$it", "Person $it", 100_000, true, it.toLong()) }
        val text = WhatsAppUpdateBuilder.build(UpdateContent("", emptyList(), "", null), lines, UpdateOptions(listLimit = 5), 0)
        assertTrue(text.contains("_…25 earlier names not shown_"))
        assertFalse(text.contains("25. Person 25"))
        assertTrue(text.contains("26. Person 26 1,000 ✅"))
        assertTrue(text.contains("31. "))
        assertTrue(text.contains("*Total received: KES 30,000*"))
    }

    @Test
    fun totalsOnly() {
        val text = WhatsAppUpdateBuilder.build(UpdateContent("", emptyList(), "", null), repeatLines, UpdateOptions(listLimit = 0), 0)
        assertFalse(text.contains("Contribution List"))
        assertTrue(text.contains("*Total received: KES 1,500*"))
        assertTrue(text.contains("Contributors: 2"))
    }

    @Test
    fun listsEveryCollectorNumber() {
        val text = WhatsAppUpdateBuilder.build(
            UpdateContent("", listOf(PayTo("Eliud Murkomen", "0723934660"), PayTo("Jane Too", "0711000111")), "", null),
            emptyList(), UpdateOptions(showTotal = false, addNextNumber = false), 0,
        )
        assertTrue(text.startsWith("Send your contribution to *Eliud Murkomen 0723 934 660* or *Jane Too 0711 000 111*"))
    }
}

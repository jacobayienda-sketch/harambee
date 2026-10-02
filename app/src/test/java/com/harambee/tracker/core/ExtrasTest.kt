package com.harambee.tracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasTest {
    @Test
    fun fillsTemplates() {
        assertEquals(
            "Hello Joel, kind reminder of your pledge of KES 500 towards Burial. Please send to Eliud 0723 934 660. Thank you 🙏",
            Templates.fill(Templates.PLEDGE_REMINDER, "Joel", 50_000, "Burial", "Eliud 0723 934 660"),
        )
        assertEquals(
            "Hello Joel, kind reminder to send your contribution towards Burial to the treasurer. Thank you 🙏",
            Templates.fill(Templates.MEMBER_REMINDER, "Joel", null, "Burial", ""),
        )
    }

    @Test
    fun matchesMembersByPhoneThenName() {
        val members = listOf(
            MemberRef(1, "Faith Kwambai", "0711111111"),
            MemberRef(2, "Faith Rutto", null),
            MemberRef(3, "CO Peter Chesos", null),
            MemberRef(4, "Noah Kiptoo", "0723456873"),
            MemberRef(5, "Noah Kiptoo Bett", "0799000000"),
        )
        val paid = listOf(
            PaidRef("Faith Kwambai", "Faith Jerop Kwambai", "254711111111", 100_000),
            PaidRef("Faith Rutto", "Faith Rutto", "0700***999", 50_000),
            PaidRef("Peter Chesos", "Peter Kiprono Chesos", null, 300_000),
            PaidRef("Noah Kiptoo", "Noah Kiptoo", "0723***873", 100_000),
            PaidRef("Stranger", "Some One", null, 100_000),
        )
        assertEquals(mapOf(1L to 100_000L, 2L to 50_000L, 3L to 300_000L, 4L to 100_000L), MemberMatcher.paidByMember(members, paid))
    }

    @Test
    fun parsesRoster() {
        val roster = RosterParser.parse(
            """
            1. Eliud Murkomen 0723934660
            2. Hillary Chebii 1,000 ✅
            3. Margaret Kiplagat
            
            4. 
            Eliud Murkomen
            """.trimIndent(),
        )
        assertEquals(listOf("Eliud Murkomen" to "0723934660", "Hillary Chebii" to null, "Margaret Kiplagat" to null), roster)
    }

    @Test
    fun closingReport() {
        val text = ClosingReport.build(
            ReportData(
                name = "Agnes' sister burial",
                firstPayment = 1_790_000_000_000, lastPayment = 1_790_200_000_000,
                lines = listOf(
                    UpdateLine("a", "Eliud Murkomen", 100_000, true, 1),
                    UpdateLine("b", "Oliver Kimutai", 50_000, true, 2),
                    UpdateLine("b", "Oliver Kimutai", 50_000, true, 3),
                    UpdateLine("c", "John cheruyot", 100_000, false, 4),
                ),
                byMethod = listOf("M-Pesa" to 150_000L, "Cash" to 50_000L),
                byCollector = listOf("Eliud" to 200_000L),
                targetCents = null, membersPaid = 2, membersTotal = 5,
                footer = "Thanks 🙏",
            ),
        )
        assertTrue(text.startsWith("*Agnes' sister burial — Final report*"))
        assertTrue(text.contains("*Total received: KES 2,000*"))
        assertTrue(text.contains("Contributors: 2"))
        assertTrue(text.contains("By method: M-Pesa 1,500 · Cash 500"))
        assertTrue(!text.contains("Received by"))
        assertTrue(text.contains("Unpaid pledges: KES 1,000 (1)"))
        assertTrue(text.contains("Members contributed: 2 of 5"))
        assertTrue(text.contains("2. Oliver Kimutai 1,000 ✅"))
        assertTrue(text.endsWith("Thanks 🙏"))
    }
}

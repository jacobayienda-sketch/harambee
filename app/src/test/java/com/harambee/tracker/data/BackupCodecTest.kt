package com.harambee.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val campaign = Campaign(id = 1, name = "Burial", startAt = 100, intro = "Good morning 🙏", targetCents = 10_000_000, memberGroup = "Sports")
    private val data = BackupData(
        campaigns = listOf(campaign),
        contributions = listOf(
            Contribution(id = 1, campaignId = 1, mpesaCode = "UIUAB8L07F", amountCents = 100_000, senderName = "Sandra Sirma", senderPhone = "0723***873",
                contributorKey = "name:sandra sirma|0723***873", receivedAt = 200, source = Source.MPESA_SMS, rawMessage = "UIUAB8L07F Confirmed…"),
            Contribution(id = 2, campaignId = 1, amountCents = 100_000, senderName = "John cheruyot", listName = "John cheruyot",
                contributorKey = "name:john cheruyot|", receivedAt = 101, source = Source.WHATSAPP_LIST, status = Status.PLEDGED),
        ),
        aliases = listOf(ContributorAlias("tel:0711111111", "CO Peter chesos")),
        collectors = listOf(Collector(id = 1, campaignId = 1, name = "Jane", phone = "0711000111")),
        members = listOf(Member(id = 1, groupName = "Sports", name = "Eliud Murkomen", phone = "0723934660", createdAt = 5)),
        activity = listOf(ActivityEntry(id = 1, at = 300, campaignId = 1, contributionId = 1, action = "Added ✅", detail = "Sandra Sirma KES 1,000")),
    )

    @Test
    fun roundTripsEverything() {
        val file = BackupFile(createdAt = 999, appVersion = "0.3.0", settings = mapOf("theme_mode" to "dark"), data = data)
        val decoded = BackupCodec.decode(BackupCodec.encode(file))
        assertEquals(file, decoded)
    }

    @Test
    fun rejectsOtherFiles() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("""{"hello":"world"}""") }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("not json") }
        val other = BackupCodec.encode(BackupFile(createdAt = 1, appVersion = "x", data = BackupData())).replace("harambee-tracker", "something-else")
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(other) }
    }

    @Test
    fun rejectsNewerFormat() {
        val newer = BackupCodec.encode(BackupFile(createdAt = 1, appVersion = "x", data = BackupData())).replace("\"format\":1", "\"format\":99")
        val e = assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(newer) }
        assertTrue(e.message!!.contains("newer version"))
    }

    @Test
    fun rejectsDamagedData() {
        val dupCodes = data.copy(contributions = data.contributions.map { it.copy(mpesaCode = "SAMECODE01") })
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(BackupCodec.encode(BackupFile(createdAt = 1, appVersion = "x", data = dupCodes)))
        }
        val orphan = data.copy(campaigns = emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(BackupCodec.encode(BackupFile(createdAt = 1, appVersion = "x", data = orphan)))
        }
    }
}

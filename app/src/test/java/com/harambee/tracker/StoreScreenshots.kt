package com.harambee.tracker

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.harambee.tracker.core.MpesaParser
import com.harambee.tracker.core.WhatsAppListParser
import com.harambee.tracker.data.Campaign
import com.harambee.tracker.data.IngestResult
import com.harambee.tracker.ui.CampaignScreen
import com.harambee.tracker.ui.DashboardScreen
import com.harambee.tracker.ui.HarambeeTheme
import com.harambee.tracker.ui.HomeScreen
import com.harambee.tracker.ui.PeopleScreen
import com.harambee.tracker.ui.ReviewScreen
import com.harambee.tracker.ui.UpdateScreen
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders real app screens with sample data for the Play Store listing.
 * All names are fictional. Run: ./gradlew testPlayDebugUnitTest -Pscreenshots --tests '*StoreScreenshots*'
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h820dp-xxhdpi")
class StoreScreenshots {
    @get:Rule val compose = createComposeRule()

    private val out = File(System.getProperty("screenshots.dir") ?: "build/store-graphics").apply { mkdirs() }
    private val app get() = ApplicationProvider.getApplicationContext<HarambeeApp>()
    private var campaignId = 0L
    private var pendingId = 0L

    private val listPost = """
        1. Grace Achieng 1,000 ✅
        2. Peter Mwangi 1,000 ✅
        3. Mary Chebet 1,000 ✅
        4. Joseph Otieno 1,000 ✅
        5. Esther Wanjiru 500 ✅
        6. CO Daniel Kiprop 3,000 ✅
        7. Ruth Njeri 1,000 ✅
        8. Samuel Kamau 1,000
        9. Lucy Atieno 1,000 ✅
        10. Brian Kiptoo 2,000 ✅
        11. Faith Muthoni 1,000 ✅
        12. Kevin Omondi 500 ✅
        13. Agnes Jepkosgei 1,000 ✅
        14. Dennis Rotich 1,000 ✅
        15. Ann Wairimu 1,500 ✅
        16. Collins Barasa 1,000
        Thanks for your generous contribution 🙏
    """.trimIndent()

    private val smsPayers = listOf(
        "MERCY WAMBUI" to 100_000L, "JAMES KORIR" to 200_000L, "NANCY AKINYI" to 50_000L, "GEORGE MUTUA" to 100_000L,
        "SAMUEL KAMAU" to 100_000L, "VIOLET NAFULA" to 100_000L, "PAUL KIBET" to 300_000L, "JANE NYAMBURA" to 100_000L,
        "TITUS OCHIENG" to 50_000L, "SHARON CHEPKOECH" to 200_000L, "MOSES WEKESA" to 100_000L, "IRENE KAGWIRIA" to 150_000L,
    )

    private fun mpesa(code: String, name: String, phone: String, cents: Long, at: LocalDateTime): String {
        val date = at.format(DateTimeFormatter.ofPattern("d/M/yy", Locale.US))
        val time = at.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
        val amount = String.format(Locale.US, "%,d.00", cents / 100)
        return "$code Confirmed.You have received Ksh$amount from $name $phone on $date at $time New M-PESA balance is Ksh12,345.00."
    }

    @Before
    fun seed() = runBlocking {
        val c = app.container
        // As on a set-up phone: notifications allowed, so no permission banner.
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        c.settings.onboarded.set(true)
        c.settings.lastBackupAt.set(System.currentTimeMillis())
        val now = LocalDateTime.now(MpesaParser.NAIROBI)
        val start = now.minusDays(4).toLocalDate().atStartOfDay(MpesaParser.NAIROBI).toInstant().toEpochMilli()
        campaignId = c.repository.saveCampaign(
            Campaign(
                name = "Mama Wanjiru's medical & burial",
                intro = "Good morning colleagues. Following the passing of our colleague's mother, the family is raising funds for the hospital bill and burial on Friday. Kindly send your generous contribution.",
                targetCents = 15_000_000,
                startAt = start,
                payToName = "Daniel Kiprop",
                payToNumber = "0712345678",
            ),
        )
        c.repository.importWhatsAppList(campaignId, WhatsAppListParser.parse(listPost))
        smsPayers.forEachIndexed { i, (name, cents) ->
            // Three days of payments, the last few earlier today.
            val at = if (i / 4 == 2) now.minusMinutes((12 - i) * 25L) else now.minusDays((2 - i / 4).toLong()).withHour(9 + i % 8).withMinute(5 * i % 60)
            val phone = "07${(10 + i)}***${(100 + i * 7)}"
            c.repository.ingestSms(mpesa("UJ${(1000 + i)}AB${i % 10}CD".take(10), name, phone, cents, at), System.currentTimeMillis(), autoConfirm = true)
        }
        val pending = c.repository.ingestSms(mpesa("UK9Z8Y7X6W", "SANDRA SIRMA", "0723***873", 100_000, now.minusMinutes(3)), System.currentTimeMillis())
        pendingId = (pending as IngestResult.Recorded).contribution.id
    }

    private fun waitFor(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun shoot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(out, "$name.png").absolutePath)
    }

    @Test fun home() {
        compose.setContent { HarambeeTheme { HomeScreen({}, {}, {}, {}, {}, {}, {}) } }
        waitFor("Mama Wanjiru")
        shoot("screenshot-1-home")
    }

    @Test fun review() {
        compose.setContent { HarambeeTheme { ReviewScreen(pendingId, {}, { _, _ -> }, {}) } }
        waitFor("New contributor")
        shoot("screenshot-2-new-payment")
    }

    @Test fun update() {
        compose.setContent { HarambeeTheme { UpdateScreen(campaignId, "FULL", null, {}, {}) } }
        waitFor("Contribution List")
        shoot("screenshot-3-whatsapp-update")
    }

    @Test fun campaign() {
        compose.setContent { HarambeeTheme { CampaignScreen(campaignId, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        waitFor("Total received")
        shoot("screenshot-4-harambee")
    }

    @Test fun dashboard() {
        compose.setContent { HarambeeTheme { DashboardScreen(campaignId, {}, {}, {}, {}) } }
        waitFor("Received per day")
        shoot("screenshot-5-dashboard")
    }

    @Test fun contributors() {
        compose.setContent { HarambeeTheme { PeopleScreen(campaignId, {}, {}) } }
        waitFor("people")
        shoot("screenshot-6-contributors")
    }
}

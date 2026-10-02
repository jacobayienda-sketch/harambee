package com.harambee.tracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.harambee.tracker.ui.AddContributionScreen
import com.harambee.tracker.ui.CampaignEditScreen
import com.harambee.tracker.ui.CampaignScreen
import com.harambee.tracker.ui.ContributionScreen
import com.harambee.tracker.ui.HarambeeTheme
import com.harambee.tracker.ui.HomeScreen
import com.harambee.tracker.ui.ImportScreen
import com.harambee.tracker.ui.ReviewListScreen
import com.harambee.tracker.ui.ReviewScreen
import com.harambee.tracker.ui.UpdateScreen

class MainActivity : ComponentActivity() {
    /** A screen requested from outside: a notification tap or text shared into the app. */
    private var request by mutableStateOf<String?>(null)
    private var sharedText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent { HarambeeTheme { AppNavigation(request, sharedText) { request = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        when {
            intent.hasExtra(EXTRA_REVIEW_ID) -> request = "review/${intent.getLongExtra(EXTRA_REVIEW_ID, 0)}"
            intent.hasExtra(EXTRA_SHARE_CAMPAIGN_ID) -> request = "update/${intent.getLongExtra(EXTRA_SHARE_CAMPAIGN_ID, 0)}"
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                request = "import?campaignId=-1&shared=true"
            }
        }
    }

    companion object {
        const val EXTRA_REVIEW_ID = "review_id"
        const val EXTRA_SHARE_CAMPAIGN_ID = "share_campaign_id"
    }
}

@Composable
private fun AppNavigation(request: String?, sharedText: String, onRequestHandled: () -> Unit) {
    val nav = rememberNavController()
    LaunchedEffect(request) {
        if (request != null) {
            nav.navigate(request) { launchSingleTop = true }
            onRequestHandled()
        }
    }
    val longArg = { name: String -> navArgument(name) { type = NavType.LongType } }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenCampaign = { nav.navigate("campaign/$it") },
                onNewCampaign = { nav.navigate("campaign-edit?id=-1") },
                onReview = { nav.navigate("review") },
                onImport = { nav.navigate("import?campaignId=-1&shared=false") },
            )
        }
        composable("campaign-edit?id={id}", listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            val id = entry.arguments!!.getLong("id").takeIf { it > 0 }
            CampaignEditScreen(
                campaignId = id,
                onDone = { savedId ->
                    nav.popBackStack()
                    if (id == null) nav.navigate("campaign/$savedId")
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable("campaign/{id}", listOf(longArg("id"))) { entry ->
            val id = entry.arguments!!.getLong("id")
            CampaignScreen(
                campaignId = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("campaign-edit?id=$id") },
                onShareUpdate = { nav.navigate("update/$id") },
                onAdd = { nav.navigate("add/$id") },
                onImport = { nav.navigate("import?campaignId=$id&shared=false") },
                onOpenContribution = { nav.navigate("contribution/$it") },
            )
        }
        composable("add/{id}", listOf(longArg("id"))) { entry ->
            AddContributionScreen(entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
        }
        composable("contribution/{id}", listOf(longArg("id"))) { entry ->
            ContributionScreen(entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
        }
        composable("update/{id}", listOf(longArg("id"))) { entry ->
            UpdateScreen(entry.arguments!!.getLong("id"), onBack = { if (!nav.popBackStack()) nav.navigate("home") })
        }
        composable("review") {
            ReviewListScreen(
                onBack = { nav.popBackStack() },
                onOpen = { nav.navigate("review/$it") },
                onShareUpdate = { id -> nav.navigate("update/$id") { popUpTo("review") { inclusive = true } } },
            )
        }
        composable("review/{id}", listOf(longArg("id"))) { entry ->
            ReviewScreen(
                contributionId = entry.arguments!!.getLong("id"),
                onBack = { nav.popBackStack() },
                // After adding, go straight to the WhatsApp update for that Harambee.
                onAdded = { campaignId -> nav.navigate("update/$campaignId") { popUpTo("review/{id}") { inclusive = true } } },
                onNewCampaign = { nav.navigate("campaign-edit?id=-1") },
            )
        }
        composable(
            "import?campaignId={campaignId}&shared={shared}",
            listOf(
                navArgument("campaignId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("shared") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            ImportScreen(
                initialCampaignId = entry.arguments!!.getLong("campaignId").takeIf { it > 0 },
                initialText = if (entry.arguments!!.getBoolean("shared")) sharedText else "",
                onBack = { nav.popBackStack() },
                onReview = { nav.navigate("review") },
                onOpenCampaign = { nav.navigate("campaign/$it") },
            )
        }
    }
}

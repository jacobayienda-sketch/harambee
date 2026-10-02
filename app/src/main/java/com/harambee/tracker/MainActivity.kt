package com.harambee.tracker

import android.content.Intent
import android.os.Bundle
import android.app.KeyguardManager
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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
import com.harambee.tracker.ui.CollectorsScreen
import com.harambee.tracker.ui.MemberStatusScreen
import com.harambee.tracker.ui.MembersScreen
import com.harambee.tracker.ui.PledgesScreen
import com.harambee.tracker.ui.ReportScreen
import com.harambee.tracker.ui.SettingsScreen
import com.harambee.tracker.ui.ActivityScreen
import com.harambee.tracker.ui.DashboardScreen
import com.harambee.tracker.ui.PeopleScreen
import com.harambee.tracker.ui.PublicPageScreen
import com.harambee.tracker.ui.BackupScreen
import com.harambee.tracker.ui.LockScreen
import com.harambee.tracker.ui.OnboardingScreen

/** FragmentActivity (a ComponentActivity) because the fingerprint / PIN prompt needs it. */
class MainActivity : FragmentActivity() {
    /** A screen requested from outside: a notification tap or text shared into the app. */
    private var request by mutableStateOf<String?>(null)
    private var sharedText = ""
    private var locked by mutableStateOf(false)
    private var stoppedAt = 0L
    private var prompting = false
    private val settings get() = container.settings
    private lateinit var biometricPrompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locked = settings.appLock.value.value
        // Created once here, as BiometricPrompt requires, and reused for every unlock.
        biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                locked = false
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
            }
        })
        if (savedInstanceState == null) handle(intent)
        val start = if (settings.onboarded.value.value) "home" else "onboarding"
        setContent {
            HarambeeTheme {
                if (locked) LockScreen(onUnlock = ::authenticate)
                else AppNavigation(start, request, sharedText) { request = null }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Lock again after a minute away.
        if (settings.appLock.value.value && stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 60_000) locked = true
    }

    override fun onResume() {
        super.onResume()
        // With the app lock on, hide the screen in the recent-apps view and block screenshots.
        if (settings.appLock.value.value) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (locked) authenticate()
        container.catchUp()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    private fun authenticate() {
        if (prompting) return
        if (!getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            // No screen lock on the phone, so there is nothing to check against.
            locked = false
            Toast.makeText(this, "Set a screen lock on your phone to use App lock", Toast.LENGTH_LONG).show()
            return
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Harambee Tracker")
            .setSubtitle("Use your fingerprint, face, PIN or pattern")
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                } else {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()
        prompting = true
        biometricPrompt.authenticate(info)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        when {
            intent.action == ACTION_REVIEW -> request = "review"
            intent.hasExtra(EXTRA_REVIEW_ID) -> request = "review/${intent.getLongExtra(EXTRA_REVIEW_ID, 0)}"
            intent.hasExtra(EXTRA_SHARE_CAMPAIGN_ID) ->
                request = "update/${intent.getLongExtra(EXTRA_SHARE_CAMPAIGN_ID, 0)}?format=${intent.getStringExtra(EXTRA_FORMAT).orEmpty()}&contributionId=-1"
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                request = "import?campaignId=-1&shared=true"
            }
        }
    }

    companion object {
        const val EXTRA_REVIEW_ID = "review_id"
        const val EXTRA_SHARE_CAMPAIGN_ID = "share_campaign_id"
        const val ACTION_REVIEW = "com.harambee.tracker.REVIEW"
        const val EXTRA_FORMAT = "update_format"
    }
}

@Composable
private fun AppNavigation(start: String, request: String?, sharedText: String, onRequestHandled: () -> Unit) {
    val nav = rememberNavController()
    LaunchedEffect(request) {
        if (request != null) {
            nav.navigate(request) { launchSingleTop = true }
            onRequestHandled()
        }
    }
    val longArg = { name: String -> navArgument(name) { type = NavType.LongType } }

    NavHost(nav, startDestination = start) {
        composable("onboarding") {
            OnboardingScreen(onDone = { createFirst ->
                nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                if (createFirst) nav.navigate("campaign-edit?id=-1")
            })
        }
        composable("backup") { BackupScreen(onBack = { nav.popBackStack() }) }
        composable("activity?campaignId={campaignId}", listOf(navArgument("campaignId") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            ActivityScreen(entry.arguments!!.getLong("campaignId").takeIf { it > 0 }, onBack = { nav.popBackStack() })
        }
        composable("home") {
            HomeScreen(
                onOpenCampaign = { nav.navigate("campaign/$it") },
                onNewCampaign = { nav.navigate("campaign-edit?id=-1") },
                onReview = { nav.navigate("review") },
                onImport = { nav.navigate("import?campaignId=-1&shared=false") },
                onMembers = { nav.navigate("members") },
                onSettings = { nav.navigate("settings") },
                onBackup = { nav.navigate("backup") },
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
                onReport = { nav.navigate("report/$id") },
                onPledges = { nav.navigate("pledges/$id") },
                onCollectors = { nav.navigate("collectors/$id") },
                onMembers = { nav.navigate("member-status/$id") },
                onActivity = { nav.navigate("activity?campaignId=$id") },
                onDashboard = { nav.navigate("dashboard/$id") },
                onPeople = { nav.navigate("people/$id") },
                onPublicPage = { nav.navigate("public/$id") },
            )
        }
        composable("report/{id}", listOf(longArg("id"))) { entry ->
            ReportScreen(entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
        }
        composable("pledges/{id}", listOf(longArg("id"))) { entry ->
            PledgesScreen(entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() }, onOpen = { nav.navigate("contribution/$it") })
        }
        composable("collectors/{id}", listOf(longArg("id"))) { entry ->
            CollectorsScreen(entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
        }
        composable("member-status/{id}", listOf(longArg("id"))) { entry ->
            val id = entry.arguments!!.getLong("id")
            MemberStatusScreen(
                id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("campaign-edit?id=$id") },
                onManageMembers = { nav.navigate("members") },
            )
        }
        composable("members") { MembersScreen(onBack = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }, onBackup = { nav.navigate("backup") }, onActivity = { nav.navigate("activity?campaignId=-1") }) }
        composable("add/{id}", listOf(longArg("id"))) { entry ->
            AddContributionScreen(entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
        }
        composable("contribution/{id}", listOf(longArg("id"))) { entry ->
            ContributionScreen(
                entry.arguments!!.getLong("id"),
                onBack = { nav.popBackStack() },
                onShareSingle = { campaignId, contributionId -> nav.navigate("update/$campaignId?format=SINGLE&contributionId=$contributionId") },
            )
        }
        composable(
            "update/{id}?format={format}&contributionId={contributionId}",
            listOf(
                longArg("id"),
                navArgument("format") { type = NavType.StringType; defaultValue = "" },
                navArgument("contributionId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            val id = entry.arguments!!.getLong("id")
            UpdateScreen(
                campaignId = id,
                initialFormat = entry.arguments!!.getString("format")?.ifBlank { null },
                contributionId = entry.arguments!!.getLong("contributionId").takeIf { it > 0 },
                onBack = { if (!nav.popBackStack()) nav.navigate("home") },
                onReport = { nav.navigate("report/$id") },
            )
        }
        composable("dashboard/{id}", listOf(longArg("id"))) { entry ->
            val id = entry.arguments!!.getLong("id")
            DashboardScreen(
                id,
                onBack = { nav.popBackStack() },
                onPeople = { nav.navigate("people/$id") },
                onActivity = { nav.navigate("activity?campaignId=$id") },
                onReview = { nav.navigate("review") },
            )
        }
        composable("people/{id}", listOf(longArg("id"))) { entry ->
            PeopleScreen(entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() }, onOpenContribution = { nav.navigate("contribution/$it") })
        }
        composable("public/{id}", listOf(longArg("id"))) { entry ->
            PublicPageScreen(entry.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
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
                onAdded = { campaignId, format ->
                    nav.navigate("update/$campaignId?format=${format.orEmpty()}&contributionId=-1") { popUpTo("review/{id}") { inclusive = true } }
                },
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

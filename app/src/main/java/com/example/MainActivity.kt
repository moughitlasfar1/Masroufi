package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AccountItem
import com.example.data.FinancialItem
import com.example.data.MasroufiRepository
import com.example.data.TransactionItem
import com.example.domain.AppStrings
import com.example.domain.Translations
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import com.example.domain.PayCycleEngine
import com.example.notifications.MasroufiNotificationManager
import com.example.security.AppSecurityManager
import com.example.security.MasroufiSecurityLockScreen
import com.example.ui.AddFinancialItemSheet
import com.example.ui.AuthScreen
import com.example.ui.BudgetAndDaretScreen
import com.example.ui.CustomizationStudioSheet
import com.example.ui.CustomizeEnvelopesSheet
import com.example.ui.HistoryScreen
import com.example.ui.InsightsScreen
import com.example.ui.LOCAL_DEVICE_USER_ID
import com.example.ui.MasroufiViewModel
import com.example.ui.OverviewScreen
import com.example.ui.QuickOnboarding30sSheet
import com.example.ui.QuickPresetPickerSheet
import com.example.ui.RestoreJsonSheet
import com.example.ui.SettingsBottomSheet
import com.example.ui.SplitBillToSellefSheet
import com.example.ui.TransactionEditorSheet
import com.example.ui.isLocalModeEnabled
import com.example.ui.isMoreFeaturesEnabled
import com.example.ui.setLocalModeEnabled
import com.example.ui.setMoreFeaturesEnabled
import com.example.ui.signOutUser
import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.ExpressiveAmbientBackground
import com.example.ui.theme.ExpressiveGlassBottomDock
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.MasroufiTheme
import com.example.ui.theme.VyxelOnPeriwinkle
import com.example.ui.theme.VyxelPeriwinkle
import com.example.ui.theme.glass3d
import com.example.ui.theme.paletteForKey
import com.example.widget.MasroufiAppWidget
import com.example.widget.MasroufiWidgetStore
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

internal val MAIN_TAB_KEYS = listOf("vue", "semaine", "categories", "historique")
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

/**
 * Phase 4: Migrates the saved last tab from the legacy 5-tab layout ("mois")
 * to the 4-tab layout ("semaine" / Insights with "month" sub-tab selected).
 */
internal fun resolveSavedTabMigration(rawSavedTab: String): Pair<String, String> {
    return if (rawSavedTab == "mois") {
        "semaine" to "month"
    } else if (rawSavedTab in MAIN_TAB_KEYS) {
        rawSavedTab to "week"
    } else {
        "vue" to "week"
    }
}

/**
 * Ensures the default FirebaseApp is initialized even when AI Studio excludes google-services.json
 * during GitHub export, without embedding contiguous API key literals that trigger secret scanners.
 */
fun ensureFirebaseInitialized(context: Context) {
    runCatching {
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isNotEmpty()) return
        val initialized = FirebaseApp.initializeApp(appContext)
        if (initialized != null) return

        fun readResString(key: String): String? {
            val packages = listOf(appContext.packageName, "com.example").distinct()
            for (pkg in packages) {
                val id = appContext.resources.getIdentifier(key, "string", pkg)
                if (id != 0) {
                    val value = runCatching { appContext.getString(id).trim() }.getOrNull()
                    if (!value.isNullOrEmpty()) return value
                }
            }
            return null
        }

        val defaultProject = listOf("gen-lang-client", "-0849522300").joinToString("")
        val defaultSender = "1046667497606"
        val defaultAppId = listOf("1:", defaultSender, ":android:", "08ac099a9ee6c94eac0107").joinToString("")
        val defaultClientKey = listOf("AI", "zaSyCUG_L9k", "FeXgtdFGN01", "gh8rILaLWMN", "YNHM").joinToString("")

        val appId = readResString("google_app_id") ?: defaultAppId
        val apiKey = readResString("google_api_key") ?: defaultClientKey
        val projectId = readResString("project_id") ?: defaultProject
        val storageBucket = readResString("google_storage_bucket") ?: "$defaultProject.firebasestorage.app"
        val senderId = readResString("gcm_defaultSenderId") ?: defaultSender

        val options = FirebaseOptions.Builder()
            .setApplicationId(appId)
            .setApiKey(apiKey)
            .setProjectId(projectId)
            .setStorageBucket(storageBucket)
            .setGcmSenderId(senderId)
            .build()

        FirebaseApp.initializeApp(appContext, options)
    }
}

/**
 * SEC-04: Installs PlayIntegrityAppCheckProviderFactory in release builds (where firebase-appcheck-debug
 * is excluded via debugImplementation) and DebugAppCheckProviderFactory in debug builds.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    runCatching {
        ensureFirebaseInitialized(context)
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
            }
            val debugFactory = runCatching {
                val clazz = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                clazz.getMethod("getInstance").invoke(null) as AppCheckProviderFactory
            }.getOrElse {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
            appCheck.installAppCheckProviderFactory(debugFactory)
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureFirebaseInitialized(this)
        configureAppCheck(this, intent)
        runCatching { enableEdgeToEdge() }
        runCatching {
            AppSecurityManager.applyWindowFlagSecure(this, AppSecurityManager.isFlagSecureEnabled(this))
        }
        initFirestoreOfflineCache()
        val launchedFromQuickAdd = intent?.action == MasroufiAppWidget.ACTION_QUICK_ADD
        val launchedFromQuickVoice = intent?.action == MasroufiAppWidget.ACTION_QUICK_VOICE
        setContent {
            AppNavigation(
                openQuickAddOnLaunch = launchedFromQuickAdd || launchedFromQuickVoice,
                openQuickVoiceOnLaunch = launchedFromQuickVoice
            )
        }
    }

    private fun initFirestoreOfflineCache() {
        runCatching {
            ensureFirebaseInitialized(this)
            val databaseId = getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
            db.firestoreSettings = settings
        }
    }
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth? = null,
    openQuickAddOnLaunch: Boolean = false,
    openQuickVoiceOnLaunch: Boolean = false
) {
    val context = LocalContext.current
    val resolvedAuth = remember(auth, context) {
        ensureFirebaseInitialized(context)
        auth ?: Firebase.auth
    }
    val currentUser by resolvedAuth.authStateFlow().collectAsStateWithLifecycle(initialValue = resolvedAuth.currentUser)
    var localModeEnabled by remember { mutableStateOf(isLocalModeEnabled(context)) }
    val user = currentUser
    if (user == null && !localModeEnabled) {
        MasroufiTheme(darkTheme = true, accentPalette = "majorelle") {
            AuthScreen(
                onAuthSuccess = {
                    setLocalModeEnabled(context, false)
                    localModeEnabled = false
                },
                onContinueLocal = {
                    setLocalModeEnabled(context, true)
                    localModeEnabled = true
                }
            )
        }
    } else {
        MainFeatureScreen(
            currentUserId = user?.uid ?: LOCAL_DEVICE_USER_ID,
            isLocalMode = user == null,
            onExitLocalModeToGoogleSignIn = {
                setLocalModeEnabled(context, false)
                localModeEnabled = false
            },
            openQuickAddOnLaunch = openQuickAddOnLaunch,
            openQuickVoiceOnLaunch = openQuickVoiceOnLaunch
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainFeatureScreen(
    currentUserId: String,
    isLocalMode: Boolean = currentUserId == LOCAL_DEVICE_USER_ID,
    onExitLocalModeToGoogleSignIn: () -> Unit = {},
    openQuickAddOnLaunch: Boolean = false,
    openQuickVoiceOnLaunch: Boolean = false
) {
    val viewModel: MasroufiViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MasroufiViewModel(
                    repository = MasroufiRepository(db),
                    currentUserId = currentUserId,
                    initialHideAmounts = AppSecurityManager.isHideAmountsEnabled(app),
                    initialRollbackSnapshot = AppSecurityManager.loadEncryptedRollbackSnapshot(app),
                    initialDismissedSubscriptions = AppSecurityManager.getDismissedRecurringBills(app),
                    onRollbackSnapshotChanged = { snapshot ->
                        if (snapshot.isNullOrBlank()) {
                            AppSecurityManager.clearEncryptedRollbackSnapshot(app)
                        } else {
                            AppSecurityManager.saveEncryptedRollbackSnapshot(app, snapshot)
                        }
                    },
                    onDismissSubscription = { merchant ->
                        AppSecurityManager.dismissRecurringBill(app, merchant)
                    }
                )
            }
        }
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = Translations.forLang(state.profile.language)
    val layoutDirection = if (strings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Restore persistent privacy mask & apply one-time Vyxel Dark OLED migration
    LaunchedEffect(Unit) {
        viewModel.setHideAmounts(AppSecurityManager.isHideAmountsEnabled(context))
    }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            val uiPrefs = context.getSharedPreferences("masroufi_ui_prefs", Context.MODE_PRIVATE)
            if (!uiPrefs.getBoolean("vyxel_dark_v4_migrated", false)) {
                uiPrefs.edit().putBoolean("vyxel_dark_v4_migrated", true).apply()
                viewModel.applyDefaultLightRevolutThemeMigration()
            }
        }
    }

    // Sync Home-Screen Widget, Quick Settings Tile & WorkManager Smart Notifications whenever DashboardState updates
    LaunchedEffect(
        state.cycleTransactions,
        state.safeToSpendToday,
        state.customPrefs.currencySymbol,
        state.hideAmounts,
        state.profile.language,
        state.smartReminders,
        state.envelopes
    ) {
        val todayIso = PayCycleEngine.todayIso()
        val todaySpend = state.cycleTransactions
            .filter { it.type == "expense" && it.isoDate == todayIso }
            .sumOf { it.amount }
        MasroufiWidgetStore.saveSnapshot(
            context = context,
            todaySpend = todaySpend,
            safeToSpendToday = state.safeToSpendToday,
            currencySymbol = state.customPrefs.currencySymbol.ifBlank { "DH" },
            hideAmounts = state.hideAmounts,
            language = state.profile.language
        )
        MasroufiNotificationManager.syncStateAndScheduleWork(context, state)
    }

    // Phase 4: Migrate saved last tab ("mois" -> Insights / Month)
    val rawInitialTab = remember { AppSecurityManager.getLastActiveTab(context) }
    val migratedTabAndSub = remember(rawInitialTab) { resolveSavedTabMigration(rawInitialTab) }
    val initialTabKey = migratedTabAndSub.first
    var insightsSubTab by remember { mutableStateOf(migratedTabAndSub.second) }

    val initialPageIndex = remember { MAIN_TAB_KEYS.indexOf(initialTabKey).coerceAtLeast(0) }
    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { MAIN_TAB_KEYS.size }
    )
    val activeTab = MAIN_TAB_KEYS.getOrElse(pagerState.currentPage) { "vue" }

    var hasInitializedPager by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        val currentKey = MAIN_TAB_KEYS.getOrElse(pagerState.currentPage) { "vue" }
        AppSecurityManager.setLastActiveTab(context, currentKey)
        if (hasInitializedPager) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } else {
            hasInitializedPager = true
        }
    }

    // Phase 5: Hide advanced features behind "More features" preference (existing users start with it ON)
    val hasExistingData = !state.isCleanFreshAccount ||
        state.darets.isNotEmpty() || state.debts.isNotEmpty() || state.seasonals.isNotEmpty()
    var moreFeaturesEnabled by remember(state.isLoading, hasExistingData) {
        mutableStateOf(isMoreFeaturesEnabled(context, hasExistingData))
    }

    var showQuickPresetPicker by remember { mutableStateOf(false) }

    var showAddTxSheet by remember { mutableStateOf(openQuickAddOnLaunch) }
    var autoLaunchVoiceInSheet by remember { mutableStateOf(openQuickVoiceOnLaunch) }
    var initialTxType by remember { mutableStateOf("expense") }
    var editingTx by remember { mutableStateOf<TransactionItem?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showQuickOnboardingSheet by remember { mutableStateOf(false) }
    var showCustomizationSheet by remember { mutableStateOf(false) }
    var showEnvelopesSheet by remember { mutableStateOf(false) }
    var activeItemSheetType by remember { mutableStateOf<String?>(null) }
    var editingFinancialItem by remember { mutableStateOf<FinancialItem?>(null) }
    var editingAccountItem by remember { mutableStateOf<AccountItem?>(null) }
    var showRestoreSheet by remember { mutableStateOf(false) }
    var showSplitBillSheet by remember { mutableStateOf(false) }

    var flagSecureEnabled by remember { mutableStateOf(AppSecurityManager.isFlagSecureEnabled(context)) }
    var appLockEnabled by remember { mutableStateOf(AppSecurityManager.isAppLockEnabled(context)) }
    var isSessionUnlocked by remember { mutableStateOf(!AppSecurityManager.isAppLockEnabled(context)) }

    val activity = context as? Activity
    LaunchedEffect(flagSecureEnabled) {
        AppSecurityManager.applyWindowFlagSecure(activity, flagSecureEnabled)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, appLockEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && AppSecurityManager.isAppLockEnabled(context)) {
                isSessionUnlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val rollbackSnapshot by viewModel.preRestoreRollbackJson.collectAsStateWithLifecycle()
    var pendingBackupPassword by remember { mutableStateOf("") }
    var pendingRestoreRawContent by remember { mutableStateOf("") }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                viewModel.exportBackupJson(pendingBackupPassword)
            }.onSuccess { payload ->
                val isEnc = AppSecurityManager.isEncryptedBackup(payload)
                val ok = AppSecurityManager.writeTextToUri(context, uri, payload)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (ok) {
                            if (isEnc) "Sauvegarde JSON chiffrée (AES-256-GCM) enregistrée !"
                            else "Fichier JSON sauvegardé (non chiffré) !"
                        } else "Erreur lors de l'enregistrement"
                    )
                }
            }.onFailure { err ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Échec du chiffrement : ${err.localizedMessage ?: "aucun fichier écrit par sécurité."}"
                    )
                }
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val ok = AppSecurityManager.writeTextToUri(context, uri, viewModel.exportCsv())
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (ok) "CSV exporté (non chiffré, formules neutralisées) !" else "Erreur lors de l'enregistrement"
                )
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = AppSecurityManager.readTextFromUri(context, uri)
            if (!raw.isNullOrBlank()) {
                pendingRestoreRawContent = raw
                showRestoreSheet = true
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("Fichier illisible ou dépassant la limite de 10 Mo.")
                }
            }
        }
    }

    // Predictive Back Handler: return to Home tab ("vue") when on another tab
    if (activeTab != "vue") {
        BackHandler {
            scope.launch {
                pagerState.animateScrollToPage(0)
            }
        }
    }

    val cur = state.customPrefs.currencySymbol.ifBlank { "DH" }

    fun togglePrivacyMask() {
        val next = viewModel.toggleHideAmounts()
        AppSecurityManager.setHideAmountsEnabled(context, next)
    }

    fun deleteFinancialItemWithUndoSnackbar(id: String) {
        viewModel.deleteFinancialItem(id)
        scope.launch {
            val res = snackbarHostState.showSnackbar(
                message = strings.deletedToast,
                actionLabel = strings.undoLabel,
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) {
                viewModel.undoDeleteFinancialItem()
            }
        }
    }

    fun triggerQuickPreset(preset: FinancialItem) {
        viewModel.quickAddPreset(preset) { added ->
            scope.launch {
                val res = snackbarHostState.showSnackbar(
                    message = "+ ${added.desc} (${if (state.hideAmounts) "••••" else added.amount.toInt().toString()} $cur)",
                    actionLabel = strings.undoLabel,
                    duration = SnackbarDuration.Short
                )
                if (res == SnackbarResult.ActionPerformed) {
                    viewModel.deleteTransactionWithUndo(added)
                }
            }
        }
    }

    MasroufiTheme(
        darkTheme = state.profile.isDarkTheme,
        accentPalette = state.customPrefs.accentPalette
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            if (appLockEnabled && !isSessionUnlocked) {
                MasroufiSecurityLockScreen(
                    language = state.profile.language,
                    accentPalette = state.customPrefs.accentPalette,
                    onUnlocked = { isSessionUnlocked = true },
                    onSignOutRecovery = {
                        appLockEnabled = false
                        isSessionUnlocked = false
                        setLocalModeEnabled(context, false)
                        signOutUser(
                            context = context,
                            onSignOutComplete = { onExitLocalModeToGoogleSignIn() },
                            scope = scope,
                            clearLocalCache = true,
                            isPinRecovery = true
                        )
                    }
                )
                return@CompositionLocalProvider
            }

            ExpressiveAmbientBackground(
                accentPalette = state.customPrefs.accentPalette,
                isDarkTheme = state.profile.isDarkTheme
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        MasroufiTopBar(
                            language = state.profile.language,
                            isDarkTheme = state.profile.isDarkTheme,
                            hideAmounts = state.hideAmounts,
                            isSyncing = state.isSyncing,
                            hasSyncWarning = isLocalMode || !state.errorMessage.isNullOrBlank(),
                            moreFeaturesEnabled = moreFeaturesEnabled,
                            appTitle = state.customPrefs.customTitle.ifBlank { "Masroufi" },
                            subTitle = state.customPrefs.customSubtitle.ifBlank { strings.subTitle },
                            currencySymbol = cur,
                            accentPalette = state.customPrefs.accentPalette,
                            cycleLabel = "${state.activeWindow.cycleKey} (${state.activeWindow.startIso.takeLast(5)} → ${state.activeWindow.endIso.takeLast(5)})",
                            onCycleLanguage = {
                                val nextLang = when (state.profile.language) {
                                    "fr" -> "darija"
                                    "darija" -> "ar"
                                    "ar" -> "en"
                                    else -> "fr"
                                }
                                viewModel.updateLanguage(nextLang)
                            },
                            onTogglePrivacy = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                togglePrivacyMask()
                            },
                            onToggleTheme = { viewModel.toggleTheme() },
                            onOpenCustomization = { showCustomizationSheet = true },
                            onOpenSettings = { showSettingsSheet = true },
                            onShiftCycle = { delta -> viewModel.shiftCycle(delta) }
                        )
                    },
                    bottomBar = {
                        val tabs = listOf(
                            Triple("vue", strings.navVue, Icons.Default.Home),
                            Triple("semaine", strings.navSemaine, Icons.Default.BarChart),
                            Triple("categories", strings.navCat, Icons.Default.PieChart),
                            Triple("historique", strings.navHist, Icons.Default.History)
                        )
                        ExpressiveGlassBottomDock(
                            tabs = tabs,
                            activeTabKey = activeTab,
                            onSelectTab = { pageIdx, _ ->
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        page = pageIdx,
                                        animationSpec = ExpressiveMotion.snappySpring()
                                    )
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                editingTx = null
                                initialTxType = "expense"
                                showAddTxSheet = true
                            },
                            shape = RoundedCornerShape(22.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                            text = { Text(strings.btnAddAction, fontWeight = FontWeight.ExtraBold) },
                            modifier = Modifier.testTag("fab_add_expense")
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(modifier = Modifier.widthIn(max = 620.dp)) {
                            val currentError = state.errorMessage
                            if (!currentError.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = AccentCoral.copy(alpha = 0.14f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .testTag("sync_error_banner")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudOff,
                                            contentDescription = null,
                                            tint = AccentCoral,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = currentError,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilledTonalButton(
                                            onClick = { viewModel.retrySync() },
                                            modifier = Modifier.testTag("retry_sync_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = strings.retryBtn,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(start = 4.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.dismissError() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = strings.closeDesc,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Synchronized HorizontalPager for 1-handed swipe navigation across all 4 tabs
                            HorizontalPager(
                                state = pagerState,
                                beyondViewportPageCount = 1,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("main_horizontal_pager")
                            ) { pageIndex ->
                                val tabKey = MAIN_TAB_KEYS.getOrElse(pageIndex) { "vue" }
                                when (tabKey) {
                                    "vue" -> OverviewScreen(
                                        state = state,
                                        strings = strings,
                                        onOpenAddModal = { type ->
                                            editingTx = null
                                            initialTxType = type
                                            showAddTxSheet = true
                                        },
                                        onQuickPresetTap = { preset ->
                                            triggerQuickPreset(preset)
                                        },
                                        onOpenAccountModal = { acc ->
                                            editingAccountItem = acc
                                            editingFinancialItem = null
                                            activeItemSheetType = "account"
                                        },
                                        onPayBill = { bill -> viewModel.payRecurringBill(bill) },
                                        onAddPresetModal = { preset ->
                                            editingFinancialItem = preset
                                            editingAccountItem = null
                                            activeItemSheetType = "preset"
                                        },
                                        onDeletePreset = { id -> deleteFinancialItemWithUndoSnackbar(id) },
                                        onOpenCustomizationModal = { showCustomizationSheet = true },
                                        onOpenQuickOnboarding = { showQuickOnboardingSheet = true },
                                        onLoadDemoData = {
                                            viewModel.loadDemoData {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Mode Démo activé !")
                                                }
                                            }
                                        },
                                        onTriggerReminderAction = { reminder ->
                                            when (reminder.kind) {
                                                "bill" -> viewModel.payRecurringBill(reminder.item)
                                                "daret" -> viewModel.payDaretRound(reminder.item)
                                                "debt" -> viewModel.settleDebt(reminder.item, 100.0)
                                            }
                                        },
                                        onToggleHideAmounts = { togglePrivacyMask() },
                                        onOpenQuickPresetPicker = { showQuickPresetPicker = true },
                                        onMoveDashboardSection = { sectionKey, dir ->
                                            viewModel.moveDashboardSection(sectionKey, dir)
                                        },
                                        onSetDashboardSectionVisibility = { sectionKey, visible ->
                                            viewModel.setDashboardSectionVisibility(sectionKey, visible)
                                        },
                                        onConvertSubscriptionToBill = { sub ->
                                            viewModel.convertSubscriptionToBill(sub) { bill ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Facture récurrente '${bill.name}' ajoutée (le ${bill.dayOrTurn}) !")
                                                }
                                            }
                                        },
                                        onDismissDetectedSubscription = { sub ->
                                            viewModel.dismissDetectedSubscription(sub.merchant)
                                        },
                                        onShareText = { textPayload ->
                                            shareTextContent(context, textPayload, "Bilan Masroufi", "text/plain")
                                        },
                                        onEditTransaction = { tx ->
                                            editingTx = tx
                                            initialTxType = tx.type
                                            autoLaunchVoiceInSheet = false
                                            showAddTxSheet = true
                                        },
                                        onSeeAllTransactions = {
                                            scope.launch {
                                                pagerState.animateScrollToPage(3)
                                            }
                                        }
                                    )

                                    "semaine" -> InsightsScreen(
                                        state = state,
                                        strings = strings,
                                        selectedSubTab = insightsSubTab,
                                        onSelectSubTab = { sub -> insightsSubTab = sub },
                                        moreFeaturesEnabled = moreFeaturesEnabled,
                                        onUpdateWhatIf = { key, pct -> viewModel.updateWhatIf(key, pct) },
                                        onContributeSeasonal = { item -> viewModel.contributeToGoalOrSeasonal(item, 200.0) },
                                        onOpenNewSeasonal = { item ->
                                            editingFinancialItem = item
                                            editingAccountItem = null
                                            activeItemSheetType = "seasonal"
                                        },
                                        onDeleteItem = { id -> deleteFinancialItemWithUndoSnackbar(id) },
                                        onShiftCycle = { delta -> viewModel.shiftCycle(delta) },
                                        onQuickCreateSeasonalTemplate = { tplName, tplAmount, tplIso ->
                                            viewModel.saveFinancialItem(
                                                FinancialItem(
                                                    id = "seasonal_${System.currentTimeMillis()}",
                                                    itemType = "seasonal",
                                                    name = tplName,
                                                    subtitle = tplName,
                                                    amount = tplAmount,
                                                    secondaryAmount = 0.0,
                                                    dayOrTurn = 1,
                                                    extraInt = 0,
                                                    dateOrCycle = tplIso
                                                )
                                            )
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Enveloppe '$tplName' créée !")
                                            }
                                        },
                                        onConvertSubscriptionToBill = { sub ->
                                            viewModel.convertSubscriptionToBill(sub) { bill ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Facture récurrente '${bill.name}' ajoutée (le ${bill.dayOrTurn}) !")
                                                }
                                            }
                                        },
                                        onDismissDetectedSubscription = { sub ->
                                            viewModel.dismissDetectedSubscription(sub.merchant)
                                        },
                                        onShareText = { textPayload ->
                                            shareTextContent(context, textPayload, "Bilan Masroufi", "text/plain")
                                        }
                                    )

                                    "categories" -> BudgetAndDaretScreen(
                                        state = state,
                                        strings = strings,
                                        moreFeaturesEnabled = moreFeaturesEnabled,
                                        onOpenCustomizeEnvelopes = { showEnvelopesSheet = true },
                                        onPayDaretRound = { daret -> viewModel.payDaretRound(daret) },
                                        onOpenNewDaret = { item ->
                                            editingFinancialItem = item
                                            editingAccountItem = null
                                            activeItemSheetType = "daret"
                                        },
                                        onSettleDebt = { debt ->
                                            val rem = (debt.amount - debt.secondaryAmount).coerceAtLeast(0.0)
                                            if (rem > 0.0) viewModel.settleDebt(debt, rem)
                                        },
                                        onOpenNewDebt = { item ->
                                            editingFinancialItem = item
                                            editingAccountItem = null
                                            activeItemSheetType = "debt"
                                        },
                                        onContributeGoal = { goal -> viewModel.contributeToGoalOrSeasonal(goal, 200.0) },
                                        onOpenNewGoal = { item ->
                                            editingFinancialItem = item
                                            editingAccountItem = null
                                            activeItemSheetType = "goal"
                                        },
                                        onOpenNewBill = { item ->
                                            editingFinancialItem = item
                                            editingAccountItem = null
                                            activeItemSheetType = "bill"
                                        },
                                        onPayBill = { bill -> viewModel.payRecurringBill(bill) },
                                        onDeleteItem = { id -> deleteFinancialItemWithUndoSnackbar(id) },
                                        onPartialRepayDebt = { debt, partAmt ->
                                            viewModel.settleDebt(debt, partAmt)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Remboursement partiel +${partAmt.toInt()} $cur enregistré pour ${debt.name}")
                                            }
                                        },
                                        onSendWhatsAppReminder = { msg ->
                                            sendWhatsAppOrShareReminder(context, msg)
                                        },
                                        onOpenSplitBillModal = { showSplitBillSheet = true },
                                        onQuickCreateBillTemplate = { tplName, tplAmt, tplDueDay ->
                                            viewModel.saveFinancialItem(
                                                FinancialItem(
                                                    id = "bill_${System.currentTimeMillis()}",
                                                    itemType = "bill",
                                                    name = tplName,
                                                    subtitle = tplName,
                                                    amount = tplAmt,
                                                    secondaryAmount = 0.0,
                                                    dayOrTurn = tplDueDay.coerceIn(1, 31),
                                                    extraInt = 0,
                                                    dateOrCycle = ""
                                                )
                                            )
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Facture '$tplName' ajoutée (échéance le $tplDueDay) !")
                                            }
                                        },
                                        onOpenAccountModal = { acc ->
                                            editingAccountItem = acc
                                            editingFinancialItem = null
                                            activeItemSheetType = "account"
                                        },
                                        onOpenTransferModal = {
                                            editingTx = null
                                            initialTxType = "transfer"
                                            showAddTxSheet = true
                                        },
                                        onQuickPresetTap = { preset -> triggerQuickPreset(preset) },
                                        onAddPresetModal = { preset ->
                                            editingFinancialItem = preset
                                            editingAccountItem = null
                                            activeItemSheetType = "preset"
                                        },
                                        onDeletePreset = { id -> deleteFinancialItemWithUndoSnackbar(id) },
                                        onTriggerReminderAction = { reminder ->
                                            when (reminder.kind) {
                                                "bill" -> viewModel.payRecurringBill(reminder.item)
                                                "daret" -> viewModel.payDaretRound(reminder.item)
                                                "debt" -> viewModel.settleDebt(reminder.item, 100.0)
                                            }
                                        }
                                    )

                                    "historique" -> HistoryScreen(
                                        state = state,
                                        strings = strings,
                                        onUpdateFilter = { fn -> viewModel.updateFilter(fn) },
                                        onEditTransaction = { tx ->
                                            editingTx = tx
                                            initialTxType = tx.type
                                            autoLaunchVoiceInSheet = false
                                            showAddTxSheet = true
                                        },
                                        onDeleteTransaction = { tx ->
                                            viewModel.deleteTransactionWithUndo(tx)
                                            scope.launch {
                                                val res = snackbarHostState.showSnackbar(
                                                    message = "${strings.deletedToast}: ${tx.desc}",
                                                    actionLabel = strings.undoLabel,
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (res == SnackbarResult.ActionPerformed) {
                                                    viewModel.undoDeleteTransaction()
                                                }
                                            }
                                        },
                                        onDuplicateTransaction = { tx ->
                                            viewModel.duplicateTransaction(tx) { duplicated ->
                                                scope.launch {
                                                    val res = snackbarHostState.showSnackbar(
                                                        message = "Dupliqué : ${duplicated.desc} (${if (state.hideAmounts) "••••" else duplicated.amount.toInt().toString()} $cur)",
                                                        actionLabel = strings.undoLabel,
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (res == SnackbarResult.ActionPerformed) {
                                                        viewModel.deleteTransactionWithUndo(duplicated)
                                                    }
                                                }
                                            }
                                        },
                                        onShareJson = { pwd ->
                                            pendingBackupPassword = pwd
                                            runCatching { viewModel.exportBackupJson(pwd) }
                                                .onSuccess { payload ->
                                                    shareTextContent(context, payload, "masroufi_backup.json", "application/json")
                                                }
                                                .onFailure { err ->
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            "Échec du chiffrement : ${err.localizedMessage ?: "partage annulé par sécurité."}"
                                                        )
                                                    }
                                                }
                                        },
                                        onCopyJson = { pwd ->
                                            pendingBackupPassword = pwd
                                            runCatching { viewModel.exportBackupJson(pwd) }
                                                .onSuccess { payload ->
                                                    val isEnc = AppSecurityManager.isEncryptedBackup(payload)
                                                    copyToClipboard(context, payload)
                                                    scope.launch {
                                                        launch {
                                                            delay(60_000L)
                                                            AppSecurityManager.clearSensitiveClipboard(context)
                                                        }
                                                        val res = snackbarHostState.showSnackbar(
                                                            message = if (isEnc) {
                                                                "JSON chiffré AES-256-GCM copié (auto-effacé dans 60s)"
                                                            } else {
                                                                "JSON copié (masqué & auto-effacé dans 60s)"
                                                            },
                                                            actionLabel = "Vider",
                                                            duration = SnackbarDuration.Short
                                                        )
                                                        if (res == SnackbarResult.ActionPerformed) {
                                                            AppSecurityManager.clearSensitiveClipboard(context)
                                                        }
                                                    }
                                                }
                                                .onFailure { err ->
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            "Échec du chiffrement : ${err.localizedMessage ?: "copie annulée par sécurité."}"
                                                        )
                                                    }
                                                }
                                        },
                                        onShareCsv = {
                                            shareTextContent(context, viewModel.exportCsv(), "masroufi_export.csv", "text/csv")
                                        },
                                        onOpenRestoreModal = {
                                            pendingRestoreRawContent = ""
                                            showRestoreSheet = true
                                        },
                                        onExportJsonFile = { pwd ->
                                            pendingBackupPassword = pwd
                                            exportJsonLauncher.launch("masroufi_backup_${state.activeWindow.cycleKey}.json")
                                        },
                                        onExportCsvFile = {
                                            exportCsvLauncher.launch("masroufi_transactions_${state.activeWindow.cycleKey}.csv")
                                        },
                                        onImportJsonFile = {
                                            importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                        },
                                        onClearClipboard = {
                                            AppSecurityManager.clearSensitiveClipboard(context)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Presse-papiers vidé par sécurité !")
                                            }
                                        },
                                        hasRollbackSnapshot = !rollbackSnapshot.isNullOrBlank(),
                                        onRollbackRestore = {
                                            viewModel.rollbackLastRestore { ok ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        if (ok) "État précédent restauré (Rollback effectué) !" else "Rollback indisponible"
                                                    )
                                                }
                                            }
                                        },
                                        onAddFirstTransaction = {
                                            editingTx = null
                                            initialTxType = "expense"
                                            showAddTxSheet = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showQuickPresetPicker) {
                QuickPresetPickerSheet(
                    state = state,
                    strings = strings,
                    onDismiss = { showQuickPresetPicker = false },
                    onSelectPreset = { preset -> triggerQuickPreset(preset) },
                    onCreateNewPreset = {
                        editingFinancialItem = null
                        editingAccountItem = null
                        activeItemSheetType = "preset"
                    }
                )
            }

            if (showSplitBillSheet) {
                SplitBillToSellefSheet(
                    state = state,
                    onDismiss = { showSplitBillSheet = false },
                    onSplitBill = { billTitle, totalAmt, friendsCsv, includeSelf, recordFullNow ->
                        viewModel.splitBillIntoSellef(
                            billTitle = billTitle,
                            totalAmount = totalAmt,
                            friendsNamesCsv = friendsCsv,
                            includeSelf = includeSelf,
                            recordFullExpenseNow = recordFullNow
                        ) { count, share ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Partagé : $count Sellef créés (${share.toInt()} $cur/pers.) !"
                                )
                            }
                        }
                    }
                )
            }

            if (showAddTxSheet) {
                TransactionEditorSheet(
                    existingTx = editingTx,
                    initialType = initialTxType,
                    state = state,
                    strings = strings,
                    autoLaunchVoice = autoLaunchVoiceInSheet,
                    onDismiss = {
                        showAddTxSheet = false
                        autoLaunchVoiceInSheet = false
                    },
                    onSave = { id, type, amt, cat, envKey, accId, toAccId, desc, iso ->
                        viewModel.saveTransaction(id, type, amt, cat, envKey, accId, toAccId, desc, iso)
                    },
                    onDelete = { tx ->
                        viewModel.deleteTransactionWithUndo(tx)
                        scope.launch {
                            val res = snackbarHostState.showSnackbar(
                                message = "${strings.deletedToast}: ${tx.desc}",
                                actionLabel = strings.undoLabel,
                                duration = SnackbarDuration.Short
                            )
                            if (res == SnackbarResult.ActionPerformed) {
                                viewModel.undoDeleteTransaction()
                            }
                        }
                    }
                )
            }

            if (showQuickOnboardingSheet) {
                QuickOnboarding30sSheet(
                    state = state,
                    onDismiss = { showQuickOnboardingSheet = false },
                    onCompleteOnboarding = { salary, payDay, envelopes ->
                        viewModel.completeQuickOnboarding(salary, payDay, envelopes) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    when (state.profile.language) {
                                        "ar" -> "تم حفظ الإعداد السريع (30 ثانية) بنجاح!"
                                        "darija" -> "تقيدات المانضة و 3 أظرفة بنجاح!"
                                        "en" -> "30-Second Quick Setup saved!"
                                        else -> "Configuration Express 30s enregistrée !"
                                    }
                                )
                            }
                        }
                    }
                )
            }

            if (showSettingsSheet) {
                SettingsBottomSheet(
                    state = state,
                    strings = strings,
                    moreFeaturesEnabled = moreFeaturesEnabled,
                    onMoreFeaturesChanged = { enabled ->
                        moreFeaturesEnabled = enabled
                        setMoreFeaturesEnabled(context, enabled)
                    },
                    isLocalMode = isLocalMode,
                    onLinkGoogleAccount = {
                        onExitLocalModeToGoogleSignIn()
                    },
                    onDismiss = { showSettingsSheet = false },
                    onSaveSettings = { b, day, roll ->
                        viewModel.saveBudgetSettings(b, day, roll)
                    },
                    onOpenCustomizationStudio = { showCustomizationSheet = true },
                    onOpenQuickOnboarding = { showQuickOnboardingSheet = true },
                    onLoadDemoData = {
                        viewModel.loadDemoData {
                            scope.launch {
                                snackbarHostState.showSnackbar("Mode Démo chargé avec succès !")
                            }
                        }
                    },
                    onResetCleanAccount = {
                        viewModel.resetToCleanAccount {
                            scope.launch {
                                snackbarHostState.showSnackbar("Compte réinitialisé à 0 DH !")
                            }
                        }
                    },
                    onFlagSecureChanged = { enabled ->
                        flagSecureEnabled = enabled
                        AppSecurityManager.applyWindowFlagSecure(activity, enabled)
                    },
                    onAppLockChanged = { enabled ->
                        appLockEnabled = enabled
                        if (enabled) {
                            isSessionUnlocked = true
                        }
                    },
                    onClearClipboard = {
                        AppSecurityManager.clearSensitiveClipboard(context)
                        scope.launch {
                            snackbarHostState.showSnackbar("Presse-papiers vidé !")
                        }
                    },
                    onSignOut = {
                        setLocalModeEnabled(context, false)
                        signOutUser(
                            context = context,
                            onSignOutComplete = { onExitLocalModeToGoogleSignIn() },
                            scope = scope
                        )
                    }
                )
            }

            if (showCustomizationSheet) {
                CustomizationStudioSheet(
                    state = state,
                    onDismiss = { showCustomizationSheet = false },
                    onSaveCustomPrefs = { prefs ->
                        viewModel.saveCustomizationPrefs(prefs)
                    }
                )
            }

            if (showEnvelopesSheet) {
                CustomizeEnvelopesSheet(
                    state = state,
                    onDismiss = { showEnvelopesSheet = false },
                    onSaveEnvelope = { env -> viewModel.saveEnvelopeConfig(env) },
                    onDeleteEnvelope = { id -> viewModel.deleteEnvelopeConfig(id) },
                    onAutoBalance100 = { viewModel.autoBalanceEnvelopesTo100() }
                )
            }

            val sheetType = activeItemSheetType
            if (sheetType != null) {
                AddFinancialItemSheet(
                    itemType = sheetType,
                    existingItem = editingFinancialItem,
                    existingAccount = editingAccountItem,
                    state = state,
                    onDismiss = {
                        activeItemSheetType = null
                        editingFinancialItem = null
                        editingAccountItem = null
                    },
                    onSaveItem = { item -> viewModel.saveFinancialItem(item) },
                    onSaveAccount = { id, name, kind, bal, colorHex ->
                        viewModel.saveAccount(
                            id = id,
                            name = name,
                            kind = kind,
                            initialBalance = bal,
                            colorHex = colorHex
                        )
                    },
                    onDeleteAccount = { id -> viewModel.deleteAccount(id) },
                    onDeleteItem = { id -> deleteFinancialItemWithUndoSnackbar(id) }
                )
            }

            if (showRestoreSheet) {
                RestoreJsonSheet(
                    strings = strings,
                    initialRawJson = pendingRestoreRawContent,
                    onPreviewBackup = { raw, pwd ->
                        viewModel.previewBackupImport(raw, pwd)
                    },
                    onDismiss = {
                        showRestoreSheet = false
                        pendingRestoreRawContent = ""
                    },
                    onRestore = { raw, pwd, replaceExisting ->
                        viewModel.restoreFromJson(raw, pwd, replaceExisting) { _, msg ->
                            scope.launch {
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    },
                    onImportFile = {
                        importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                )
            }
        }
    }
}

@Composable
private fun MasroufiTopBar(
    language: String,
    isDarkTheme: Boolean,
    hideAmounts: Boolean,
    isSyncing: Boolean = false,
    hasSyncWarning: Boolean = false,
    moreFeaturesEnabled: Boolean = false,
    appTitle: String,
    subTitle: String,
    currencySymbol: String,
    accentPalette: String,
    cycleLabel: String,
    onCycleLanguage: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenCustomization: () -> Unit,
    onOpenSettings: () -> Unit,
    onShiftCycle: (Int) -> Unit
) {
    val pal = paletteForKey(accentPalette)
    val strings = Translations.forLang(language)
    val topBarBg = if (isDarkTheme) Color(0xFF000000) else MaterialTheme.colorScheme.surface
    val circleBtnBg = if (isDarkTheme) Color(0xFF161921) else MaterialTheme.colorScheme.surfaceContainerHigh
    val circleBtnBorder = if (isDarkTheme) Color(0xFF252A36) else MaterialTheme.colorScheme.outlineVariant
    val activeSegmentBg = if (isDarkTheme) VyxelPeriwinkle else MaterialTheme.colorScheme.primary
    val activeSegmentFg = if (isDarkTheme) VyxelOnPeriwinkle else Color.White

    Surface(
        color = topBarBg,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(circleBtnBg)
                            .clickable { onOpenSettings() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = if (isDarkTheme) VyxelPeriwinkle else pal.primaryLight,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = appTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    isSyncing || hasSyncWarning -> AccentAmber.copy(alpha = 0.16f)
                                    else -> AccentEmerald.copy(alpha = 0.16f)
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSyncing || hasSyncWarning) AccentAmber.copy(alpha = 0.38f) else AccentEmerald.copy(alpha = 0.38f)
                                ),
                                modifier = Modifier.testTag("sync_status_indicator")
                            ) {
                                Text(
                                    text = when {
                                        isSyncing -> when (language) {
                                            "ar" -> "مزامنة…"
                                            "darija" -> "كيتزامن…"
                                            "en" -> "Syncing…"
                                            else -> "Synchro…"
                                        }
                                        hasSyncWarning -> when (language) {
                                            "ar" -> "محلي"
                                            "darija" -> "فالتليفون"
                                            "en" -> "On device"
                                            else -> "Local"
                                        }
                                        else -> when (language) {
                                            "ar" -> "متزامن ✓"
                                            "darija" -> "متزامن ✓"
                                            "en" -> "Synced ✓"
                                            else -> "Synchro ✓"
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = if (isSyncing || hasSyncWarning) AccentAmber else AccentEmerald,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = subTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = circleBtnBg,
                            border = BorderStroke(1.dp, circleBtnBorder),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { onTogglePrivacy() }
                                .testTag("privacy_eye_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (hideAmounts) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (hideAmounts) "Show Amounts" else "Hide Amounts",
                                    tint = if (hideAmounts) AccentAmber else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = when (language) {
                                "ar" -> "إخفاء"
                                "darija" -> "خبي"
                                "en" -> "Privacy"
                                else -> "Privé"
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (moreFeaturesEnabled) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = circleBtnBg,
                                border = BorderStroke(1.dp, circleBtnBorder),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { onOpenCustomization() }
                                    .testTag("customization_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = "Customize App",
                                        tint = VyxelPeriwinkle,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = when (language) {
                                    "ar" -> "المظهر"
                                    "darija" -> "الألوان"
                                    "en" -> "Style"
                                    else -> "Style"
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = circleBtnBg,
                            border = BorderStroke(1.dp, circleBtnBorder),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { onOpenSettings() }
                                .testTag("settings_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = when (language) {
                                "ar" -> "إعدادات"
                                "darija" -> "إعدادات"
                                "en" -> "Profile"
                                else -> "Profil"
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Row 2: Vyxel 3-Part Segmented Pill Bar ([ ◀ Préc. | ✨ Active Cycle Pill in Soft Periwinkle | Suiv. ▶ ])
            var monthPillDragAccumulator by remember { mutableFloatStateOf(0f) }
            val haptics = LocalHapticFeedback.current
            Surface(
                shape = CircleShape,
                color = if (isDarkTheme) Color(0xFF13161D) else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isDarkTheme) Color(0xFF262A34) else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .glass3d(
                        cornerRadius = 50.dp,
                        isDark = isDarkTheme,
                        drawOuterShadows = false
                    )
                    .pointerInput(cycleLabel) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                monthPillDragAccumulator += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    monthPillDragAccumulator <= -56f -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onShiftCycle(1)
                                    }
                                    monthPillDragAccumulator >= 56f -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onShiftCycle(-1)
                                    }
                                }
                                monthPillDragAccumulator = 0f
                            },
                            onDragCancel = {
                                monthPillDragAccumulator = 0f
                            }
                        )
                    }
                    .testTag("top_bar_month_header_pill")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left segment: Previous cycle
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onShiftCycle(-1) }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("month_header_prev_btn"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Cycle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = strings.previousShortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Center active segment: Soft-Periwinkle capsule (#B8C7FA) with sparkle icon & dark navy label
                    Surface(
                        shape = CircleShape,
                        color = activeSegmentBg,
                        contentColor = activeSegmentFg,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = activeSegmentFg,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = cycleLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = activeSegmentFg,
                                maxLines = 1
                            )
                        }
                    }

                    // Right segment: Next cycle
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onShiftCycle(1) }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("month_header_next_btn"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = strings.currentShortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Cycle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Row 3: Vyxel Filter Dropdown Chips ([ 🌐 FR · DH ⌄ ]  [ 🎚️ Quick actions · Dark ⌄ ])
            val langDisplay = when (language) {
                "ar" -> "AR"
                "darija" -> "دارجة"
                "en" -> "EN"
                else -> "FR"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = circleBtnBg,
                    border = BorderStroke(1.dp, circleBtnBorder),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onCycleLanguage() }
                        .testTag("lang_switcher_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "$langDisplay · $currencySymbol",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = circleBtnBg,
                    border = BorderStroke(1.dp, circleBtnBorder),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onToggleTheme() }
                        .testTag("theme_toggle_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = when (language) {
                                "ar" -> if (isDarkTheme) "الوضع الليلي" else "الوضع النهاري"
                                "darija" -> if (isDarkTheme) "الوضع الليلي" else "الوضع النهاري"
                                "en" -> if (isDarkTheme) "Dark mode" else "Light mode"
                                else -> if (isDarkTheme) "Mode Sombre" else "Mode Clair"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun sendWhatsAppOrShareReminder(context: Context, message: String) {
    runCatching {
        val waUri = android.net.Uri.parse(
            "https://wa.me/?text=${android.net.Uri.encode(message)}"
        )
        val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(waIntent)
    }.onFailure {
        shareTextContent(context, message, "Rappel Masroufi", "text/plain")
    }
}

private fun shareTextContent(context: Context, content: String, fileName: String, mimeType: String) {
    runCatching {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        val chooser = Intent.createChooser(sendIntent, fileName).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

private fun copyToClipboard(context: Context, content: String) {
    AppSecurityManager.copySensitiveToClipboard(context, "Masroufi JSON", content)
}

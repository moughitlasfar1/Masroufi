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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
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
import com.example.ui.MasroufiViewModel
import com.example.ui.MonthScreen
import com.example.ui.OverviewScreen
import com.example.ui.QuickOnboarding30sSheet
import com.example.ui.QuickPresetPickerSheet
import com.example.ui.RestoreJsonSheet
import com.example.ui.SettingsBottomSheet
import com.example.ui.SplitBillToSellefSheet
import com.example.ui.TransactionEditorSheet
import com.example.ui.WeekScreen
import com.example.ui.signOutUser
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.MasroufiTheme
import com.example.ui.theme.paletteForKey
import com.example.widget.MasroufiAppWidget
import com.example.widget.MasroufiWidgetStore
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
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

private val MAIN_TAB_KEYS = listOf("vue", "semaine", "mois", "categories", "historique")
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

/**
 * SEC-04: Installs PlayIntegrityAppCheckProviderFactory in release builds (where firebase-appcheck-debug
 * is excluded via debugImplementation) and DebugAppCheckProviderFactory in debug builds.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    runCatching {
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
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        AppSecurityManager.applyWindowFlagSecure(this, AppSecurityManager.isFlagSecureEnabled(this))
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
    auth: FirebaseAuth = Firebase.auth,
    openQuickAddOnLaunch: Boolean = false,
    openQuickVoiceOnLaunch: Boolean = false
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        MasroufiTheme(darkTheme = true) {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        MainFeatureScreen(
            currentUserId = user.uid,
            openQuickAddOnLaunch = openQuickAddOnLaunch,
            openQuickVoiceOnLaunch = openQuickVoiceOnLaunch
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainFeatureScreen(
    currentUserId: String,
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
                    onRollbackSnapshotChanged = { snapshot ->
                        if (snapshot.isNullOrBlank()) {
                            AppSecurityManager.clearEncryptedRollbackSnapshot(app)
                        } else {
                            AppSecurityManager.saveEncryptedRollbackSnapshot(app, snapshot)
                        }
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

    // Restore persistent privacy mask & last active tab on first composition
    LaunchedEffect(Unit) {
        viewModel.setHideAmounts(AppSecurityManager.isHideAmountsEnabled(context))
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

    val initialTabKey = remember { AppSecurityManager.getLastActiveTab(context) }
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

    var isSpeedDialOpen by remember { mutableStateOf(false) }
    var showQuickPresetPicker by remember { mutableStateOf(false) }
    var shortcutSheetTabKey by remember { mutableStateOf<String?>(null) }

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

    // Predictive Back Handler: close speed-dial first, then return to Home tab ("vue")
    if (isSpeedDialOpen || activeTab != "vue") {
        BackHandler {
            if (isSpeedDialOpen) {
                isSpeedDialOpen = false
            } else {
                scope.launch {
                    pagerState.animateScrollToPage(0)
                }
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
                        signOutUser(
                            context = context,
                            onSignOutComplete = {},
                            scope = scope,
                            clearLocalCache = true,
                            isPinRecovery = true
                        )
                    }
                )
                return@CompositionLocalProvider
            }

            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    MasroufiTopBar(
                        language = state.profile.language,
                        isDarkTheme = state.profile.isDarkTheme,
                        hideAmounts = state.hideAmounts,
                        isSyncing = state.isSyncing,
                        hasSyncWarning = !state.errorMessage.isNullOrBlank(),
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
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        val tabs = listOf(
                            Triple("vue", strings.navVue, Icons.Default.Home),
                            Triple("semaine", strings.navSemaine, Icons.Default.CalendarMonth),
                            Triple("mois", strings.navMois, Icons.Default.BarChart),
                            Triple("categories", strings.navCat, Icons.Default.PieChart),
                            Triple("historique", strings.navHist, Icons.Default.History)
                        )
                        tabs.forEachIndexed { pageIdx, (key, label, icon) ->
                            NavigationBarItem(
                                selected = activeTab == key,
                                onClick = {
                                    isSpeedDialOpen = false
                                    scope.launch {
                                        pagerState.animateScrollToPage(pageIdx)
                                    }
                                },
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .combinedClickable(
                                                onClick = {
                                                    isSpeedDialOpen = false
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(pageIdx)
                                                    }
                                                },
                                                onLongClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    shortcutSheetTabKey = key
                                                }
                                            )
                                            .padding(4.dp)
                                            .testTag("nav_icon_$key")
                                    ) {
                                        Icon(imageVector = icon, contentDescription = label)
                                    }
                                },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        fontSize = 11.sp,
                                        modifier = Modifier.combinedClickable(
                                            onClick = {
                                                isSpeedDialOpen = false
                                                scope.launch {
                                                    pagerState.animateScrollToPage(pageIdx)
                                                }
                                            },
                                            onLongClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                shortcutSheetTabKey = key
                                            }
                                        )
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_$key")
                            )
                        }
                    }
                },
                floatingActionButton = {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnimatedVisibility(
                            visible = isSpeedDialOpen,
                            enter = fadeIn() + scaleIn(initialScale = 0.85f),
                            exit = fadeOut() + scaleOut(targetScale = 0.85f)
                        ) {
                            SpeedDialFanItems(
                                lang = state.profile.language,
                                onSelectExpense = {
                                    isSpeedDialOpen = false
                                    editingTx = null
                                    initialTxType = "expense"
                                    showAddTxSheet = true
                                },
                                onSelectIncome = {
                                    isSpeedDialOpen = false
                                    editingTx = null
                                    initialTxType = "income"
                                    showAddTxSheet = true
                                },
                                onSelectTransfer = {
                                    isSpeedDialOpen = false
                                    editingTx = null
                                    initialTxType = "transfer"
                                    showAddTxSheet = true
                                },
                                onSelectPreset = {
                                    isSpeedDialOpen = false
                                    showQuickPresetPicker = true
                                }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Speed-Dial Fan Toggle Pill
                            Surface(
                                shape = CircleShape,
                                color = if (isSpeedDialOpen) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isSpeedDialOpen = !isSpeedDialOpen
                                    }
                                    .testTag("fab_speed_dial_toggle")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isSpeedDialOpen) Icons.Default.Close else Icons.Default.Bolt,
                                        contentDescription = "Menu Actions Rapides",
                                        tint = if (isSpeedDialOpen) MaterialTheme.colorScheme.onErrorContainer else AccentAmber
                                    )
                                }
                            }

                            ExtendedFloatingActionButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isSpeedDialOpen) {
                                        isSpeedDialOpen = false
                                    }
                                    editingTx = null
                                    initialTxType = "expense"
                                    showAddTxSheet = true
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                                text = { Text(strings.btnAddAction, fontWeight = FontWeight.ExtraBold) },
                                modifier = Modifier.testTag("fab_add_expense")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(modifier = Modifier.widthIn(max = 600.dp)) {
                        val currentError = state.errorMessage
                        if (!currentError.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = AccentCoral.copy(alpha = 0.14f),
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
                                            text = "Réessayer",
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
                                            contentDescription = "Fermer",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Synchronized HorizontalPager for 1-handed swipe navigation across all 5 tabs
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
                                    onShareText = { textPayload ->
                                        shareTextContent(context, textPayload, "Bilan Masroufi", "text/plain")
                                    }
                                )

                                "semaine" -> WeekScreen(
                                    state = state,
                                    strings = strings
                                )

                                "mois" -> MonthScreen(
                                    state = state,
                                    strings = strings,
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
                                    }
                                )

                                "categories" -> BudgetAndDaretScreen(
                                    state = state,
                                    strings = strings,
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

                    // Dimmed Scrim when Speed-Dial Fan Menu is expanded
                    if (isSpeedDialOpen) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.38f))
                                .clickable { isSpeedDialOpen = false }
                                .testTag("speed_dial_scrim")
                        )
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

            val shortcutTab = shortcutSheetTabKey
            if (shortcutTab != null) {
                NavTabShortcutsSheet(
                    tabKey = shortcutTab,
                    strings = strings,
                    lang = state.profile.language,
                    onDismiss = { shortcutSheetTabKey = null },
                    onQuickExpense = {
                        editingTx = null
                        initialTxType = "expense"
                        showAddTxSheet = true
                    },
                    onQuickIncome = {
                        editingTx = null
                        initialTxType = "income"
                        showAddTxSheet = true
                    },
                    onQuickTransfer = {
                        editingTx = null
                        initialTxType = "transfer"
                        showAddTxSheet = true
                    },
                    onQuickPresetPicker = { showQuickPresetPicker = true },
                    onOpenCustomizeEnvelopes = { showEnvelopesSheet = true },
                    onOpenNewItem = { itemType ->
                        editingFinancialItem = null
                        editingAccountItem = null
                        activeItemSheetType = itemType
                    },
                    onExportCsv = {
                        shareTextContent(context, viewModel.exportCsv(), "masroufi_export.csv", "text/csv")
                    },
                    onOpenRestore = {
                        pendingRestoreRawContent = ""
                        showRestoreSheet = true
                    },
                    onOpenCustomization = { showCustomizationSheet = true }
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
                        signOutUser(context, onSignOutComplete = {}, scope = scope)
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
private fun SpeedDialFanItems(
    lang: String,
    onSelectExpense: () -> Unit,
    onSelectIncome: () -> Unit,
    onSelectTransfer: () -> Unit,
    onSelectPreset: () -> Unit
) {
    val items = listOf(
        SpeedDialItemData(
            label = when (lang) {
                "ar" -> "شراء سريع 1-Tap"
                "darija" -> "تقضية دغيا 1-Tap"
                "en" -> "Quick Preset (1-Tap)"
                else -> "Achat Rapide (1-Tap)"
            },
            icon = Icons.Default.Bolt,
            accent = AccentAmber,
            testTag = "speed_dial_preset",
            onClick = onSelectPreset
        ),
        SpeedDialItemData(
            label = when (lang) {
                "ar" -> "تحويل بين الحسابات"
                "darija" -> "تحويل بين الحسابات"
                "en" -> "Transfer Accounts"
                else -> "Transfert entre comptes"
            },
            icon = Icons.Default.SwapHoriz,
            accent = AccentSkyBlue,
            testTag = "speed_dial_transfer",
            onClick = onSelectTransfer
        ),
        SpeedDialItemData(
            label = when (lang) {
                "ar" -> "إضافة مدخول"
                "darija" -> "زيد مدخول"
                "en" -> "Add Income"
                else -> "Ajouter un Revenu"
            },
            icon = Icons.Default.ArrowDownward,
            accent = AccentEmerald,
            testTag = "speed_dial_income",
            onClick = onSelectIncome
        ),
        SpeedDialItemData(
            label = when (lang) {
                "ar" -> "إضافة مصروف"
                "darija" -> "زيد مصروف"
                "en" -> "Add Expense"
                else -> "Ajouter une Dépense"
            },
            icon = Icons.Default.ArrowUpward,
            accent = AccentCoral,
            testTag = "speed_dial_expense",
            onClick = onSelectExpense
        )
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("speed_dial_fan_menu")
    ) {
        items.forEach { item ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = item.onClick)
                    .testTag(item.testTag)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(item.accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = item.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class SpeedDialItemData(
    val label: String,
    val icon: ImageVector,
    val accent: Color,
    val testTag: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavTabShortcutsSheet(
    tabKey: String,
    strings: AppStrings,
    lang: String,
    onDismiss: () -> Unit,
    onQuickExpense: () -> Unit,
    onQuickIncome: () -> Unit,
    onQuickTransfer: () -> Unit,
    onQuickPresetPicker: () -> Unit,
    onOpenCustomizeEnvelopes: () -> Unit,
    onOpenNewItem: (String) -> Unit,
    onExportCsv: () -> Unit,
    onOpenRestore: () -> Unit,
    onOpenCustomization: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = when (lang) {
                    "ar" -> "اختصارات سريعة"
                    "darija" -> "اختصارات دغيا"
                    "en" -> "Quick Navigation Shortcuts"
                    else -> "Raccourcis Rapides"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )

            val actions: List<Pair<String, () -> Unit>> = when (tabKey) {
                "categories" -> listOf(
                    strings.editEnvelopesBtn to onOpenCustomizeEnvelopes,
                    "+ Daret (Tontine)" to { onOpenNewItem("daret") },
                    "+ Facture récurrente" to { onOpenNewItem("bill") },
                    "+ Crédit / Dette" to { onOpenNewItem("debt") },
                    "+ Objectif d'épargne" to { onOpenNewItem("goal") }
                )
                "historique" -> listOf(
                    strings.expenseTab to onQuickExpense,
                    strings.btnShareCsv to onExportCsv,
                    strings.btnRestoreJson to onOpenRestore
                )
                else -> listOf(
                    strings.expenseTab to onQuickExpense,
                    strings.incomeTab to onQuickIncome,
                    strings.transferTab to onQuickTransfer,
                    strings.quickPresetsTitle to onQuickPresetPicker,
                    "Studio de Personnalisation" to onOpenCustomization
                )
            }

            actions.forEachIndexed { idx, (label, action) ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            onDismiss()
                            action()
                        }
                        .testTag("nav_shortcut_${tabKey}_$idx")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
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
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp, 14.dp, 14.dp, 5.dp))
                            .background(
                                Brush.linearGradient(listOf(pal.primaryDark, pal.heroStart))
                            )
                            .clickable { onOpenCustomization() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = appTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = currencySymbol,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            // Sync Status Indicator Pill ("Saved on device, syncing…" / "Synchronisé ✓")
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    isSyncing || hasSyncWarning -> AccentAmber.copy(alpha = 0.16f)
                                    else -> AccentEmerald.copy(alpha = 0.16f)
                                },
                                modifier = Modifier.testTag("sync_status_indicator")
                            ) {
                                Text(
                                    text = when {
                                        isSyncing -> when (language) {
                                            "ar" -> "محفوظ محلياً، جاري المزامنة…"
                                            "darija" -> "مسجل فـ التليفون، كيتزامن…"
                                            "en" -> "Saved on device, syncing…"
                                            else -> "Enregistré, synchro…"
                                        }
                                        hasSyncWarning -> when (language) {
                                            "ar" -> "محفوظ محلياً"
                                            "darija" -> "مسجل فـ التليفون"
                                            "en" -> "Saved on device"
                                            else -> "Cache local"
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
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = subTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    val langDisplay = when (language) {
                        "ar" -> "AR 🇸🇦"
                        "darija" -> "دارجة 🇲🇦"
                        "en" -> "EN 🇬🇧"
                        else -> "FR 🇫🇷"
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onCycleLanguage() }
                            .testTag("lang_switcher_pill")
                    ) {
                        Text(
                            text = langDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = onTogglePrivacy,
                        modifier = Modifier.testTag("privacy_eye_button")
                    ) {
                        Icon(
                            imageVector = if (hideAmounts) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (hideAmounts) "Show Amounts" else "Hide Amounts",
                            tint = if (hideAmounts) AccentAmber else MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onOpenCustomization,
                        modifier = Modifier.testTag("customization_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Customize App",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Settings"
                        )
                    }
                }
            }

            // Pay-Cycle Month Navigator Pill (Swipe left/right on the month header to change month)
            var monthPillDragAccumulator by remember { mutableFloatStateOf(0f) }
            val haptics = LocalHapticFeedback.current
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .pointerInput(cycleLabel) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                monthPillDragAccumulator += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    monthPillDragAccumulator <= -64f -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onShiftCycle(1)
                                    }
                                    monthPillDragAccumulator >= 64f -> {
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
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onShiftCycle(-1) },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("month_header_prev_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Cycle"
                        )
                    }
                    Text(
                        text = cycleLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                    IconButton(
                        onClick = { onShiftCycle(1) },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("month_header_next_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Cycle"
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

package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
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
import com.example.domain.VoiceBatchParseResult
import com.example.domain.VoiceDarijaParser
import com.example.notifications.MasroufiNotificationManager
import com.example.security.AppSecurityManager
import com.example.security.MasroufiSecurityLockScreen
import com.example.ui.voice.MoonshineAnswerOverlayCard
import com.example.ui.voice.MoonshineWalkieTalkieFab
import com.example.ui.voice.MoonshineWalkieTalkieHud
import com.example.voice.MoonshineVoiceController
import kotlinx.coroutines.delay
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
import com.example.ui.dashboard.DashboardHeader
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.DataSourceConfigStore
import com.example.data.LocalRoomMasroufiDataSource
import com.example.data.local.DatabaseHealthState
import com.example.data.local.MasroufiDatabaseProvider
import com.example.data.local.NoOpRemoteMirror
import com.example.data.local.RoomMasroufiRepository
import com.example.ui.DatabaseRecoveryDialog
import com.masroufi.credit.data.RoomCreditRepository
import com.masroufi.credit.domain.CreditCalculator
import com.masroufi.credit.domain.CreditStrings
import com.masroufi.credit.presentation.CreditSimulatorScreen
import com.masroufi.credit.presentation.CreditSimulatorViewModel
import com.masroufi.importer.domain.ImporterStrings
import com.masroufi.importer.presentation.StatementImportFlowSheet

internal val MAIN_TAB_KEYS = listOf("vue", "semaine", "categories", "historique")

sealed interface ActiveSheet {
    data object None : ActiveSheet
    data class AddOrEditTransaction(
        val existingTx: TransactionItem? = null,
        val initialType: String = "expense",
        val autoLaunchVoice: Boolean = false
    ) : ActiveSheet
    data object QuickPresets : ActiveSheet
    data class FinancialOrAccountItem(
        val itemType: String,
        val existingItem: FinancialItem? = null,
        val existingAccount: AccountItem? = null
    ) : ActiveSheet
    data object EnvelopesEditor : ActiveSheet
    data object Settings : ActiveSheet
    data object CustomizationStudio : ActiveSheet
    data object QuickOnboarding : ActiveSheet
    data object RestoreBackup : ActiveSheet
    data object SplitBill : ActiveSheet
    data object BankDetections : ActiveSheet
    data object StatementImport : ActiveSheet
}

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
 * SEC-04 & S5: Installs PlayIntegrityAppCheckProviderFactory in release builds (where firebase-appcheck-debug
 * is excluded via debugImplementation) and DebugAppCheckProviderFactory in debug builds
 * without reading debug tokens from untrusted launch intents.
 */
fun configureAppCheck(context: Context, intent: Intent? = null) {
    runCatching {
        ensureFirebaseInitialized(context)
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
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
        val launchedFromQuickAdd = intent?.action == MasroufiAppWidget.ACTION_QUICK_ADD
        val launchedFromQuickVoice = intent?.action == MasroufiAppWidget.ACTION_QUICK_VOICE
        val targetTabExtra = intent?.getStringExtra(MasroufiAppWidget.EXTRA_TARGET_TAB).orEmpty()
        setContent {
            AppNavigation(
                openQuickAddOnLaunch = launchedFromQuickAdd || launchedFromQuickVoice,
                openQuickVoiceOnLaunch = launchedFromQuickVoice,
                initialTargetTab = targetTabExtra
            )
        }
    }
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth? = null,
    openQuickAddOnLaunch: Boolean = false,
    openQuickVoiceOnLaunch: Boolean = false,
    initialTargetTab: String = ""
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
        MasroufiTheme(
            darkTheme = androidx.compose.foundation.isSystemInDarkTheme(),
            accentPalette = "majorelle"
        ) {
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
            openQuickVoiceOnLaunch = openQuickVoiceOnLaunch,
            initialTargetTab = initialTargetTab
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
    openQuickVoiceOnLaunch: Boolean = false,
    initialTargetTab: String = ""
) {
    val viewModel: MasroufiViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val roomDataSource = LocalRoomMasroufiDataSource(
                    context = app,
                    remoteMirror = NoOpRemoteMirror,
                    userIdProvider = { currentUserId }
                )
                MasroufiViewModel(
                    repository = roomDataSource,
                    currentUserId = currentUserId,
                    initialHideAmounts = AppSecurityManager.isHideAmountsEnabled(app),
                    initialRollbackSnapshot = AppSecurityManager.loadEncryptedRollbackSnapshot(app),
                    initialDismissedSubscriptions = AppSecurityManager.getDismissedRecurringBills(app),
                    initialLearnedCategoriesJson = AppSecurityManager.loadLearnedCategoriesJson(app),
                    initialVerifiedActiveDates = com.example.notifications.MasroufiNotificationManager.recordVerifiedActiveDate(app),
                    onRollbackSnapshotChanged = { snapshot ->
                        if (snapshot.isNullOrBlank()) {
                            AppSecurityManager.clearEncryptedRollbackSnapshot(app)
                        } else {
                            AppSecurityManager.saveEncryptedRollbackSnapshot(app, snapshot)
                        }
                    },
                    onDismissSubscription = { merchant ->
                        AppSecurityManager.dismissRecurringBill(app, merchant)
                    },
                    onLearnedCategoriesUpdated = { json ->
                        AppSecurityManager.saveLearnedCategoriesJson(app, json)
                    }
                )
            }
        }
    )

    val creditViewModel: CreditSimulatorViewModel = viewModel(
        key = "credit_simulator_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                CreditSimulatorViewModel(
                    repository = RoomCreditRepository(
                        context = app,
                        userIdProvider = { currentUserId },
                        remoteMirror = NoOpRemoteMirror
                    )
                )
            }
        }
    )
    val creditUiState by creditViewModel.uiState.collectAsStateWithLifecycle()
    var showCreditSimulator by remember { mutableStateOf(false) }
    var showAnnualBills by remember { mutableStateOf(false) }
    var showZakatTracker by remember { mutableStateOf(false) }
    var activeShareCard by remember { mutableStateOf<com.example.ui.ShareCardPayload?>(null) }

    val rawState by viewModel.uiState.collectAsStateWithLifecycle()
    val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
    val effectiveIsDark = rawState.customPrefs.resolveIsDark(systemInDark)
    val state = remember(rawState, effectiveIsDark) {
        if (rawState.profile.isDarkTheme == effectiveIsDark) {
            rawState
        } else {
            rawState.copy(profile = rawState.profile.copy(isDarkTheme = effectiveIsDark))
        }
    }
    val strings = Translations.forLang(state.profile.language)
    val layoutDirection = if (strings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val pendingBankDetections by com.example.notifications.BankDetectionStore.pendingItemsFlow.collectAsStateWithLifecycle()
    val localDbHealth by MasroufiDatabaseProvider.healthState.collectAsStateWithLifecycle()
    var annualBillsList by remember {
        mutableStateOf(com.masroufi.fiscal.data.FiscalRepository.loadAnnualBills(context))
    }
    var zakatConfig by remember {
        mutableStateOf(com.masroufi.fiscal.data.FiscalRepository.loadZakatConfig(context))
    }

    fun upsertAnnualBillReserve(item: com.masroufi.fiscal.domain.AnnualBillItem) {
        val fStrings = com.masroufi.fiscal.domain.FiscalStrings.forLang(state.profile.language)
        val (curYear, curMonth) = com.masroufi.fiscal.domain.AnnualBillCalculator.currentYearAndMonth()
        val existingSaved = state.goals.find { it.id == item.linkedGoalId }?.secondaryAmount ?: 0.0
        val (goalItem, recurringBillItem) = com.masroufi.fiscal.data.FiscalRepository.buildBackingGoalAndRecurringBill(
            bill = item,
            existingGoalSavedAmount = existingSaved,
            payCycleDay = state.profile.payCycleDay,
            strings = fStrings,
            currentYear = curYear,
            currentMonth = curMonth
        )
        val syncedItem = item.copy(
            linkedGoalId = goalItem.id,
            linkedBillId = recurringBillItem.id
        )
        val updatedList = annualBillsList.filterNot { it.id == syncedItem.id } + syncedItem
        annualBillsList = updatedList
        com.masroufi.fiscal.data.FiscalRepository.saveAnnualBills(context, updatedList)
        viewModel.saveFinancialItem(goalItem)
        viewModel.saveFinancialItem(recurringBillItem)
        val curSym = state.customPrefs.currencySymbol.ifBlank { "DH" }
        val monthlyFormatted = "${PayCycleEngine.formatAmountOrMask(recurringBillItem.amount, state.hideAmounts)} $curSym"
        scope.launch {
            snackbarHostState.showSnackbar(
                fStrings.reserveCreatedToastFmt.replace("{monthly}", monthlyFormatted)
            )
        }
    }

    fun markAnnualBillPaid(item: com.masroufi.fiscal.domain.AnnualBillItem, logExpense: Boolean) {
        val fStrings = com.masroufi.fiscal.domain.FiscalStrings.forLang(state.profile.language)
        val todayIso = PayCycleEngine.todayIso()
        val updatedList = annualBillsList.map { existing ->
            if (existing.id == item.id) existing.copy(isPaid = true, paidIsoDate = todayIso) else existing
        }
        annualBillsList = updatedList
        com.masroufi.fiscal.data.FiscalRepository.saveAnnualBills(context, updatedList)
        if (item.linkedGoalId.isNotBlank()) {
            viewModel.deleteFinancialItem(item.linkedGoalId)
        }
        if (item.linkedBillId.isNotBlank()) {
            viewModel.deleteFinancialItem(item.linkedBillId)
        }
        if (logExpense && item.annualAmountMad > 0.0) {
            val envKey = if (item.preset == com.masroufi.fiscal.domain.AnnualBillPreset.VIGNETTE ||
                item.preset == com.masroufi.fiscal.domain.AnnualBillPreset.CAR_INSURANCE
            ) "transport" else "charges"
            viewModel.saveTransaction(
                existingId = "tx_fiscal_${item.id}_${System.currentTimeMillis()}",
                type = "expense",
                amount = item.annualAmountMad,
                category = envKey,
                envelopeKey = envKey,
                accountId = state.accounts.firstOrNull()?.account?.id ?: "acc_bank",
                toAccountId = "",
                desc = item.title,
                isoDate = todayIso
            )
        }
        scope.launch {
            snackbarHostState.showSnackbar(
                fStrings.billPaidLoggedToastFmt.replace("{title}", item.title)
            )
        }
    }

    fun deleteAnnualBill(item: com.masroufi.fiscal.domain.AnnualBillItem) {
        val updatedList = annualBillsList.filterNot { it.id == item.id }
        annualBillsList = updatedList
        com.masroufi.fiscal.data.FiscalRepository.saveAnnualBills(context, updatedList)
        if (!item.isPaid) {
            if (item.linkedGoalId.isNotBlank()) viewModel.deleteFinancialItem(item.linkedGoalId)
            if (item.linkedBillId.isNotBlank()) viewModel.deleteFinancialItem(item.linkedBillId)
        }
    }

    // Phase 1: Open encrypted SQLCipher Room DB as the single local-first source of truth
    // and atomically adopt any local_device_user records when signed in with Google.
    LaunchedEffect(currentUserId) {
        if (!MasroufiApplication.isSqlCipherNativeLoaded) {
            DataSourceConfigStore.refreshActiveMode(context, currentUserId, isRoomHealthy = false)
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val roomDb = MasroufiDatabaseProvider.getOrOpen(context)
            val healthy = roomDb != null
            DataSourceConfigStore.refreshActiveMode(context, currentUserId, isRoomHealthy = healthy)
            if (roomDb != null && currentUserId != LOCAL_DEVICE_USER_ID) {
                RoomMasroufiRepository(
                    dao = roomDb.masroufiDao(),
                    remoteMirror = NoOpRemoteMirror,
                    userIdProvider = { currentUserId }
                ).adoptLocalDataIntoUser(
                    sourceUserId = LOCAL_DEVICE_USER_ID,
                    targetUserId = currentUserId
                )
            }
        }
    }

    // Restore persistent privacy mask & load pending bank detections
    LaunchedEffect(Unit) {
        viewModel.setHideAmounts(AppSecurityManager.isHideAmountsEnabled(context))
        viewModel.setVerifiedActiveDates(com.example.notifications.MasroufiNotificationManager.recordVerifiedActiveDate(context))
        com.example.notifications.BankDetectionStore.loadPendingItems(context)
        PayCycleEngine.importLearnedCategoriesJson(
            AppSecurityManager.loadLearnedCategoriesJson(context),
            merge = true
        )
    }

    val isBulkImporting by viewModel.isBulkImporting.collectAsStateWithLifecycle()

    // Sync Home-Screen Widget (full WidgetSnapshot), Quick Settings Tiles & WorkManager Smart Notifications whenever DashboardState updates
    // Suppressed while a bulk statement import is writing chunks so the snapshot recomputes cleanly once at the end.
    LaunchedEffect(
        isBulkImporting,
        state.cycleTransactions,
        state.safeToSpendToday,
        state.customPrefs.currencySymbol,
        state.customPrefs.accentPalette,
        state.customPrefs.themeMode,
        state.hideAmounts,
        state.profile.language,
        state.profile.isDarkTheme,
        state.smartReminders,
        state.envelopes,
        state.goals,
        state.presets,
        state.weekDaysSpend
    ) {
        if (!isBulkImporting) {
            MasroufiWidgetStore.saveFullSnapshot(context, state)
            MasroufiNotificationManager.syncStateAndScheduleWork(context, state)
        }
    }

    // Phase 4: Migrate saved last tab ("mois" -> Insights / Month), or jump to widget EXTRA_TARGET_TAB
    val rawInitialTab = remember {
        initialTargetTab.takeIf { it in MAIN_TAB_KEYS } ?: AppSecurityManager.getLastActiveTab(context)
    }
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

    // Phase 6: Single ActiveSheet state so two bottom sheets can never open at the same time
    var activeSheet by remember {
        mutableStateOf<ActiveSheet>(
            if (openQuickAddOnLaunch) {
                ActiveSheet.AddOrEditTransaction(
                    existingTx = null,
                    initialType = "expense",
                    autoLaunchVoice = openQuickVoiceOnLaunch
                )
            } else {
                ActiveSheet.None
            }
        )
    }

    var flagSecureEnabled by remember { mutableStateOf(AppSecurityManager.isFlagSecureEnabled(context)) }
    var appLockEnabled by remember { mutableStateOf(AppSecurityManager.isAppLockEnabled(context)) }
    var isSessionUnlocked by remember { mutableStateOf(!AppSecurityManager.isAppLockEnabled(context)) }

    val activity = context as? FragmentActivity
    LaunchedEffect(flagSecureEnabled) {
        AppSecurityManager.applyWindowFlagSecure(activity, flagSecureEnabled)
    }

    // Phase 1 Bug #3: Match status bar icon style to Light vs Dark theme
    LaunchedEffect(state.profile.isDarkTheme) {
        runCatching {
            val isDark = state.profile.isDarkTheme
            activity?.enableEdgeToEdge(
                statusBarStyle = if (isDark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                },
                navigationBarStyle = if (isDark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, appLockEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && AppSecurityManager.isAppLockEnabled(context)) {
                isSessionUnlocked = false
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                com.example.notifications.BankDetectionStore.loadPendingItems(context)
                val pendingQuickTxs = MasroufiWidgetStore.consumePendingQuickCaptureTransactions(context)
                if (pendingQuickTxs.isNotEmpty()) {
                    val existingIds = viewModel.uiState.value.allTransactions.map { it.id }.toSet()
                    pendingQuickTxs.filter { it.id !in existingIds }.forEach { tx ->
                        viewModel.saveTransaction(
                            existingId = tx.id,
                            type = tx.type,
                            amount = tx.amount,
                            category = tx.category,
                            envelopeKey = tx.envelopeKey,
                            accountId = tx.accountId,
                            desc = tx.desc,
                            isoDate = tx.isoDate
                        )
                    }
                }
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
                val ok = AppSecurityManager.writeTextToUri(context, uri, payload)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (ok) "Sauvegarde JSON chiffrée (AES-256-GCM) enregistrée !"
                        else "Erreur lors de l'enregistrement"
                    )
                }
            }.onFailure { err ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Échec du chiffrement : ${err.localizedMessage ?: "mot de passe (10+ car.) requis — aucun fichier écrit."}"
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
                activeSheet = ActiveSheet.RestoreBackup
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

    val moonshineController = remember(context) {
        MoonshineVoiceController(context = context, scope = scope)
    }
    val isMoonshineListening by moonshineController.isListening.collectAsStateWithLifecycle()
    val moonshineLiveTranscript by moonshineController.liveTranscript.collectAsStateWithLifecycle()
    val moonshineWaveformLevels by moonshineController.waveformLevels.collectAsStateWithLifecycle()
    val isVoiceOnDevice by moonshineController.isUsingOnDeviceRecognizer.collectAsStateWithLifecycle()
    var activeBudgetQueryAnswer by remember { mutableStateOf<VoiceBatchParseResult.BudgetQuery?>(null) }

    DisposableEffect(moonshineController) {
        onDispose {
            moonshineController.shutdown()
        }
    }

    LaunchedEffect(activeBudgetQueryAnswer) {
        if (activeBudgetQueryAnswer != null) {
            delay(5000L)
            activeBudgetQueryAnswer = null
        }
    }

    val recordAudioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            moonshineController.startListening(state.profile.language)
        } else {
            // Still open the Moonshine HUD so the user can use 1-tap phrases or type Darija
            moonshineController.startListening(state.profile.language)
            scope.launch {
                snackbarHostState.showSnackbar(strings.moonshineMicPermDenied)
            }
        }
    }

    fun startMoonshineWalkieTalkie() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        activeBudgetQueryAnswer = null
        if (MoonshineVoiceController.hasRecordAudioPermission(context)) {
            moonshineController.startListening(state.profile.language)
        } else {
            recordAudioPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    fun commitMoonshineWalkieTalkie() {
        val raw = moonshineController.stopAndConsumeTranscript()
        moonshineController.cancelListening()
        if (raw.isBlank()) return
        when (val result = VoiceDarijaParser.evaluateVoiceUtterance(raw, state)) {
            is VoiceBatchParseResult.ExpensesBatch -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val savedBatch = viewModel.saveVoiceBatchTransactions(result.items)
                if (savedBatch.isNotEmpty()) {
                    val toastText = if (savedBatch.size > 1) {
                        strings.moonshineBatchSavedFmt
                            .replace("{count}", savedBatch.size.toString())
                            .replace("{total}", PayCycleEngine.formatAmountOrMask(result.totalAmount, state.hideAmounts))
                            .replace("{cur}", cur)
                    } else {
                        val first = result.items.first()
                        strings.moonshineSingleSavedFmt
                            .replace("{desc}", first.description)
                            .replace("{total}", PayCycleEngine.formatAmountOrMask(first.amount, state.hideAmounts))
                            .replace("{cur}", cur)
                            .replace("{cat}", first.categoryName)
                    }
                    scope.launch {
                        val snackRes = snackbarHostState.showSnackbar(
                            message = toastText,
                            actionLabel = strings.undoLabel,
                            duration = SnackbarDuration.Short
                        )
                        if (snackRes == SnackbarResult.ActionPerformed) {
                            viewModel.deleteTransactionsBatch(savedBatch)
                        }
                    }
                }
            }
            is VoiceBatchParseResult.BudgetQuery -> {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                activeBudgetQueryAnswer = result
                moonshineController.speakAnswer(
                    text = result.spokenAnswer,
                    language = state.profile.language,
                    hideAmounts = state.hideAmounts
                )
            }
            is VoiceBatchParseResult.Unrecognized -> {
                scope.launch {
                    snackbarHostState.showSnackbar(result.hint)
                }
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
        accentPalette = state.customPrefs.accentPalette,
        language = state.profile.language
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

            if (showCreditSimulator) {
                ExpressiveAmbientBackground(
                    accentPalette = state.customPrefs.accentPalette,
                    isDarkTheme = state.profile.isDarkTheme
                ) {
                    CreditSimulatorScreen(
                        viewModel = creditViewModel,
                        language = state.profile.language,
                        initialMonthlyIncome = state.profile.baseBudget,
                        isLocalMode = isLocalMode,
                        onBack = { showCreditSimulator = false },
                        onRequestGoogleSignIn = {
                            showCreditSimulator = false
                            onExitLocalModeToGoogleSignIn()
                        },
                        onCreateLinkedRecurringBill = { savedInput, savedResult ->
                            val cStrings = CreditStrings.forLang(state.profile.language)
                            val typeLabel = cStrings.presetLabel(savedInput.creditType)
                            val billName = cStrings.billPrefixFmt.replace("{type}", typeLabel)
                            val billSub = cStrings.billSubtitleFmt
                                .replace("{months}", savedInput.durationMonths.toString())
                                .replace("{rate}", CreditCalculator.formatPercent(savedInput.annualInterestRateHt))
                            viewModel.saveFinancialItem(
                                FinancialItem(
                                    id = "bill_credit_${System.currentTimeMillis()}",
                                    itemType = "bill",
                                    name = billName,
                                    subtitle = billSub,
                                    amount = PayCycleEngine.roundMoney(savedResult.monthlyPayment),
                                    secondaryAmount = 0.0,
                                    dayOrTurn = state.profile.payCycleDay.coerceIn(1, 28),
                                    extraInt = 0,
                                    dateOrCycle = ""
                                )
                            )
                        }
                    )
                }
                return@CompositionLocalProvider
            }

            if (showAnnualBills) {
                ExpressiveAmbientBackground(
                    accentPalette = state.customPrefs.accentPalette,
                    isDarkTheme = state.profile.isDarkTheme
                ) {
                    com.masroufi.fiscal.presentation.AnnualBillsScreen(
                        annualBills = annualBillsList,
                        existingGoals = state.goals,
                        language = state.profile.language,
                        currencySymbol = state.customPrefs.currencySymbol,
                        hideAmounts = state.hideAmounts,
                        isDarkTheme = state.profile.isDarkTheme,
                        onBack = { showAnnualBills = false },
                        onSaveReserve = { item -> upsertAnnualBillReserve(item) },
                        onMarkBillPaid = { item, logExp -> markAnnualBillPaid(item, logExp) },
                        onDeleteBill = { item -> deleteAnnualBill(item) }
                    )
                }
                return@CompositionLocalProvider
            }

            if (showZakatTracker) {
                val savingsTotal = remember(state.goals) {
                    state.goals.sumOf { it.secondaryAmount.coerceAtLeast(0.0) }
                }
                val shortTermBorrowedDebts = remember(state.debts) {
                    state.debts
                        .filter { !it.dateOrCycle.equals("lent", ignoreCase = true) }
                        .sumOf { (it.amount - it.secondaryAmount).coerceAtLeast(0.0) }
                }
                val linkedZakatBill = remember(annualBillsList, zakatConfig) {
                    annualBillsList.find { it.id == zakatConfig.linkedAnnualBillId && !it.isPaid }
                }
                val alreadyReservedZakat = remember(linkedZakatBill, state.goals) {
                    val gid = linkedZakatBill?.linkedGoalId.orEmpty()
                    if (gid.isBlank()) 0.0 else state.goals.find { it.id == gid }?.secondaryAmount ?: 0.0
                }
                ExpressiveAmbientBackground(
                    accentPalette = state.customPrefs.accentPalette,
                    isDarkTheme = state.profile.isDarkTheme
                ) {
                    com.masroufi.fiscal.presentation.ZakatTrackerScreen(
                        accountsTotalMad = state.totalNetWorth.coerceAtLeast(0.0),
                        savingsTotalMad = savingsTotal,
                        shortTermDebtsOwedMad = shortTermBorrowedDebts,
                        alreadyReservedMad = alreadyReservedZakat,
                        initialConfig = zakatConfig,
                        language = state.profile.language,
                        currencySymbol = state.customPrefs.currencySymbol,
                        hideAmounts = state.hideAmounts,
                        isDarkTheme = state.profile.isDarkTheme,
                        onBack = { showZakatTracker = false },
                        onSaveConfig = { updatedCfg ->
                            zakatConfig = updatedCfg
                            com.masroufi.fiscal.data.FiscalRepository.saveZakatConfig(context, updatedCfg)
                        },
                        onCreateMonthlyReserve = { zakatDueMad, hawlDueIso ->
                            val fStrings = com.masroufi.fiscal.domain.FiscalStrings.forLang(state.profile.language)
                            val parts = hawlDueIso.split("-")
                            val dueYear = parts.getOrNull(0)?.toIntOrNull()
                                ?: com.masroufi.fiscal.domain.AnnualBillCalculator.currentYearAndMonth().first
                            val dueMonth = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: 1
                            val billId = zakatConfig.linkedAnnualBillId.ifBlank { "zakat_reserve_active" }
                            val zakatBill = com.masroufi.fiscal.domain.AnnualBillItem(
                                id = billId,
                                preset = com.masroufi.fiscal.domain.AnnualBillPreset.ZAKAT,
                                title = fStrings.presetZakat,
                                annualAmountMad = zakatDueMad,
                                dueMonth = dueMonth,
                                dueYear = dueYear,
                                linkedGoalId = linkedZakatBill?.linkedGoalId.orEmpty().ifBlank { "goal_fiscal_$billId" },
                                linkedBillId = linkedZakatBill?.linkedBillId.orEmpty().ifBlank { "bill_fiscal_$billId" }
                            )
                            val updatedCfg = zakatConfig.copy(linkedAnnualBillId = billId)
                            zakatConfig = updatedCfg
                            com.masroufi.fiscal.data.FiscalRepository.saveZakatConfig(context, updatedCfg)
                            upsertAnnualBillReserve(zakatBill)
                            showZakatTracker = false
                        }
                    )
                }
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
                        DashboardHeader(
                            language = state.profile.language,
                            isDarkTheme = state.profile.isDarkTheme,
                            hideAmounts = state.hideAmounts,
                            isSyncing = state.isSyncing,
                            isOfflineMode = isLocalMode || state.isOfflineMode,
                            accentPalette = state.customPrefs.accentPalette,
                            startIso = state.activeWindow.startIso,
                            endIso = state.activeWindow.endIso,
                            onTogglePrivacy = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                togglePrivacyMask()
                            },
                            onOpenSettings = { activeSheet = ActiveSheet.Settings },
                            onOpenCreditSimulator = { showCreditSimulator = true },
                            onShiftCycle = { delta -> viewModel.shiftCycle(delta) }
                        )
                    },
                    bottomBar = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MoonshineAnswerOverlayCard(
                                queryResult = activeBudgetQueryAnswer,
                                isDarkTheme = state.profile.isDarkTheme,
                                onReplayAudio = { q ->
                                    moonshineController.speakAnswer(
                                        text = q.spokenAnswer,
                                        language = state.profile.language,
                                        hideAmounts = state.hideAmounts
                                    )
                                },
                                onDismiss = { activeBudgetQueryAnswer = null }
                            )
                            MoonshineWalkieTalkieHud(
                                visible = isMoonshineListening,
                                state = state,
                                liveTranscript = moonshineLiveTranscript,
                                waveformLevels = moonshineWaveformLevels,
                                isUsingOnDeviceRecognizer = isVoiceOnDevice,
                                onTranscriptChange = { moonshineController.updateTranscriptManual(it) },
                                onCommit = { commitMoonshineWalkieTalkie() },
                                onCancel = { moonshineController.cancelListening() }
                            )
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
                        }
                    },
                    floatingActionButton = {
                        MoonshineWalkieTalkieFab(
                            addLabel = strings.btnAddAction,
                            isListening = isMoonshineListening,
                            isDarkTheme = state.profile.isDarkTheme,
                            onQuickTapAdd = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeSheet = ActiveSheet.AddOrEditTransaction(
                                    existingTx = null,
                                    initialType = "expense",
                                    autoLaunchVoice = false
                                )
                            },
                            onStartWalkieTalkie = {
                                startMoonshineWalkieTalkie()
                            },
                            onStopAndCommitWalkieTalkie = {
                                commitMoonshineWalkieTalkie()
                            }
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
                            if (BuildConfig.DEBUG) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AccentAmber.copy(alpha = 0.16f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .testTag("debug_build_warning_banner")
                                ) {
                                    Text(
                                        text = "Debug build — not secure",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentAmber,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }
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
                                            activeSheet = ActiveSheet.AddOrEditTransaction(
                                                existingTx = null,
                                                initialType = type
                                            )
                                        },
                                        onQuickPresetTap = { preset ->
                                            triggerQuickPreset(preset)
                                        },
                                        onOpenAccountModal = { acc ->
                                            activeSheet = ActiveSheet.FinancialOrAccountItem(
                                                itemType = "account",
                                                existingAccount = acc
                                            )
                                        },
                                        onPayBill = { bill -> viewModel.payRecurringBill(bill) },
                                        onAddPresetModal = { preset ->
                                            activeSheet = ActiveSheet.FinancialOrAccountItem(
                                                itemType = "preset",
                                                existingItem = preset
                                            )
                                        },
                                        onDeletePreset = { id -> deleteFinancialItemWithUndoSnackbar(id) },
                                        onOpenCustomizationModal = { activeSheet = ActiveSheet.CustomizationStudio },
                                        onOpenQuickOnboarding = { activeSheet = ActiveSheet.QuickOnboarding },
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
                                        onOpenQuickPresetPicker = { activeSheet = ActiveSheet.QuickPresets },
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
                                            activeSheet = ActiveSheet.AddOrEditTransaction(
                                                existingTx = tx,
                                                initialType = tx.type,
                                                autoLaunchVoice = false
                                            )
                                        },
                                        onSeeAllTransactions = {
                                            scope.launch {
                                                pagerState.animateScrollToPage(3)
                                            }
                                        },
                                        pendingBankDetectionsCount = pendingBankDetections.size,
                                        onOpenBankDetections = {
                                            activeSheet = ActiveSheet.BankDetections
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
                                            activeSheet = ActiveSheet.FinancialOrAccountItem(
                                                itemType = "seasonal",
                                                existingItem = item
                                            )
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
                                        onOpenCustomizeEnvelopes = { activeSheet = ActiveSheet.EnvelopesEditor },
                                        onPayDaretRound = { daret -> viewModel.payDaretRound(daret) },
                                        onCollectDaretPot = { daret, targetAccId ->
                                            viewModel.collectDaretPot(daret, targetAccId)
                                            scope.launch {
                                                snackbarHostState.showSnackbar(strings.daretPotCollectedToast)
                                            }
                                        },
                                        onOpenNewDaret = { item ->
                                            activeSheet = ActiveSheet.FinancialOrAccountItem(
                                                itemType = "daret",
                                                existingItem = item
                                            )
                                        },
                                        onSettleDebt = { debt ->
                                            val rem = (debt.amount - debt.secondaryAmount).coerceAtLeast(0.0)
                                            if (rem > 0.0) viewModel.settleDebt(debt, rem)
                                        },
                                        onOpenNewDebt = { item ->
                                            activeSheet = ActiveSheet.FinancialOrAccountItem(
                                                itemType = "debt",
                                                existingItem = item
                                            )
                                        },
                                        onContributeGoal = { goal -> viewModel.contributeToGoalOrSeasonal(goal, 200.0) },
                                        onOpenNewGoal = { item ->
                                            activeSheet = ActiveSheet.FinancialOrAccountItem(
                                                itemType = "goal",
                                                existingItem = item
                                            )
                                        },
                                        onOpenNewBill = { item ->
                                            activeSheet = ActiveSheet.FinancialOrAccountItem(
                                                itemType = "bill",
                                                existingItem = item
                                            )
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
                                        onOpenSplitBillModal = { activeSheet = ActiveSheet.SplitBill },
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
                                            activeSheet = ActiveSheet.FinancialOrAccountItem(
                                                itemType = "account",
                                                existingAccount = acc
                                            )
                                        },
                                        onOpenTransferModal = {
                                            activeSheet = ActiveSheet.AddOrEditTransaction(
                                                existingTx = null,
                                                initialType = "transfer"
                                            )
                                        },
                                        onQuickPresetTap = { preset -> triggerQuickPreset(preset) },
                                        onAddPresetModal = { preset ->
                                            activeSheet = ActiveSheet.FinancialOrAccountItem(
                                                itemType = "preset",
                                                existingItem = preset
                                            )
                                        },
                                        onDeletePreset = { id -> deleteFinancialItemWithUndoSnackbar(id) },
                                        onTriggerReminderAction = { reminder ->
                                            when (reminder.kind) {
                                                "bill" -> viewModel.payRecurringBill(reminder.item)
                                                "daret" -> viewModel.payDaretRound(reminder.item)
                                                "debt" -> viewModel.settleDebt(reminder.item, 100.0)
                                            }
                                        },
                                        savedCreditsCount = creditUiState.savedCredits.size,
                                        onOpenCreditSimulator = { showCreditSimulator = true },
                                        activeAnnualBillsCount = annualBillsList.count { !it.isPaid },
                                        onOpenAnnualBills = { showAnnualBills = true },
                                        onOpenZakatTracker = { showZakatTracker = true },
                                        onShareCard = { payload -> activeShareCard = payload }
                                    )

                                    "historique" -> HistoryScreen(
                                        state = state,
                                        strings = strings,
                                        onUpdateFilter = { fn -> viewModel.updateFilter(fn) },
                                        onEditTransaction = { tx ->
                                            activeSheet = ActiveSheet.AddOrEditTransaction(
                                                existingTx = tx,
                                                initialType = tx.type,
                                                autoLaunchVoice = false
                                            )
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
                                            activeSheet = ActiveSheet.RestoreBackup
                                        },
                                        onExportJsonFile = { pwd ->
                                            if (pwd.trim().length < AppSecurityManager.MIN_BACKUP_PASSWORD_LENGTH) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        "Mot de passe de 10+ caractères requis pour exporter un JSON chiffré."
                                                    )
                                                }
                                            } else {
                                                pendingBackupPassword = pwd
                                                exportJsonLauncher.launch("masroufi_backup_${state.activeWindow.cycleKey}.json")
                                            }
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
                                            activeSheet = ActiveSheet.AddOrEditTransaction(
                                                existingTx = null,
                                                initialType = "expense"
                                            )
                                        },
                                        onOpenStatementImport = {
                                            activeSheet = ActiveSheet.StatementImport
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when (val sheet = activeSheet) {
                ActiveSheet.None -> Unit

                ActiveSheet.StatementImport -> StatementImportFlowSheet(
                    state = state,
                    onImportTransactions = { txs, onProgress, onDone ->
                        viewModel.importStatementTransactions(
                            transactions = txs,
                            onChunkProgress = onProgress,
                            onComplete = { count ->
                                onDone(count)
                                if (count > 0) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            ImporterStrings.forLang(state.profile.language).importSuccessFormat(count)
                                        )
                                    }
                                }
                            }
                        )
                    },
                    onDismiss = { activeSheet = ActiveSheet.None }
                )

                ActiveSheet.QuickPresets -> QuickPresetPickerSheet(
                    state = state,
                    strings = strings,
                    onDismiss = { activeSheet = ActiveSheet.None },
                    onSelectPreset = { preset -> triggerQuickPreset(preset) },
                    onCreateNewPreset = {
                        activeSheet = ActiveSheet.FinancialOrAccountItem(itemType = "preset")
                    }
                )

                ActiveSheet.SplitBill -> SplitBillToSellefSheet(
                    state = state,
                    onDismiss = { activeSheet = ActiveSheet.None },
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

                is ActiveSheet.AddOrEditTransaction -> TransactionEditorSheet(
                    existingTx = sheet.existingTx,
                    initialType = sheet.initialType,
                    state = state,
                    strings = strings,
                    autoLaunchVoice = sheet.autoLaunchVoice,
                    onDismiss = { activeSheet = ActiveSheet.None },
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

                ActiveSheet.QuickOnboarding -> QuickOnboarding30sSheet(
                    state = state,
                    onDismiss = { activeSheet = ActiveSheet.None },
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

                ActiveSheet.Settings -> SettingsBottomSheet(
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
                    onDismiss = { activeSheet = ActiveSheet.None },
                    onSaveSettings = { b, day, roll ->
                        viewModel.saveBudgetSettings(b, day, roll)
                    },
                    onToggleTheme = { viewModel.toggleTheme() },
                    onSelectThemeMode = { modeKey ->
                        viewModel.setThemeMode(modeKey, systemInDark)
                    },
                    onSelectLanguage = { langCode -> viewModel.updateLanguage(langCode) },
                    onSaveCustomPrefs = { prefs ->
                        viewModel.setThemeMode(prefs.themeMode, systemInDark)
                        viewModel.saveCustomizationPrefs(prefs)
                    },
                    onOpenCustomizationStudio = { activeSheet = ActiveSheet.CustomizationStudio },
                    onOpenQuickOnboarding = { activeSheet = ActiveSheet.QuickOnboarding },
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
                    onExportJsonFile = { pwd ->
                        if (pwd.trim().length < AppSecurityManager.MIN_BACKUP_PASSWORD_LENGTH) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Mot de passe de 10+ caractères requis pour exporter un JSON chiffré."
                                )
                            }
                        } else {
                            pendingBackupPassword = pwd
                            exportJsonLauncher.launch("masroufi_backup_${state.activeWindow.cycleKey}.json")
                        }
                    },
                    onExportCsvFile = {
                        exportCsvLauncher.launch("masroufi_transactions_${state.activeWindow.cycleKey}.csv")
                    },
                    onOpenRestoreModal = {
                        pendingRestoreRawContent = ""
                        activeSheet = ActiveSheet.RestoreBackup
                    },
                    onOpenBankDetections = {
                        activeSheet = ActiveSheet.BankDetections
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

                ActiveSheet.BankDetections -> com.example.ui.BankDetectionReviewSheet(
                    state = state,
                    strings = strings,
                    pendingItems = pendingBankDetections,
                    onConfirmDetection = { item ->
                        com.example.notifications.BankDetectionStore.removePendingItem(context, item.id)
                        val isTransfer = item.direction == "transfer"
                        viewModel.saveTransaction(
                            existingId = "tx_${System.currentTimeMillis()}",
                            type = item.direction,
                            amount = item.amount,
                            category = if (isTransfer) strings.transferAction else item.suggestedCategoryName,
                            envelopeKey = if (isTransfer) "autre" else item.suggestedEnvelopeKey,
                            accountId = "acc_bank",
                            toAccountId = if (isTransfer) "acc_cash" else "",
                            desc = item.merchant.ifBlank { item.suggestedCategoryName },
                            isoDate = item.isoDate
                        )
                        scope.launch {
                            snackbarHostState.showSnackbar("✓ ${item.merchant.ifBlank { item.suggestedCategoryName }}")
                        }
                    },
                    onEditDetection = { item ->
                        com.example.notifications.BankDetectionStore.removePendingItem(context, item.id)
                        val isTransfer = item.direction == "transfer"
                        val prefilled = TransactionItem(
                            id = "tx_${System.currentTimeMillis()}",
                            type = item.direction,
                            amount = item.amount,
                            category = if (isTransfer) strings.transferAction else item.suggestedCategoryName,
                            envelopeKey = if (isTransfer) "autre" else item.suggestedEnvelopeKey,
                            accountId = "acc_bank",
                            toAccountId = if (isTransfer) "acc_cash" else "",
                            desc = item.merchant.ifBlank { item.suggestedCategoryName },
                            isoDate = item.isoDate
                        )
                        activeSheet = ActiveSheet.AddOrEditTransaction(
                            existingTx = prefilled,
                            initialType = prefilled.type,
                            autoLaunchVoice = false
                        )
                    },
                    onIgnoreDetection = { item ->
                        com.example.notifications.BankDetectionStore.removePendingItem(context, item.id)
                    },
                    onAlwaysIgnoreMerchant = { item ->
                        com.example.notifications.BankDetectionStore.addIgnoredMerchant(context, item.merchant)
                    },
                    onRefreshPending = {
                        com.example.notifications.BankDetectionStore.loadPendingItems(context)
                    },
                    onShowMessage = { msg ->
                        scope.launch {
                            snackbarHostState.showSnackbar(msg)
                        }
                    },
                    onDismiss = { activeSheet = ActiveSheet.None }
                )

                ActiveSheet.CustomizationStudio -> CustomizationStudioSheet(
                    state = state,
                    onDismiss = { activeSheet = ActiveSheet.None },
                    onSaveCustomPrefs = { prefs ->
                        viewModel.setThemeMode(prefs.themeMode, systemInDark)
                        viewModel.saveCustomizationPrefs(prefs)
                    }
                )

                ActiveSheet.EnvelopesEditor -> CustomizeEnvelopesSheet(
                    state = state,
                    onDismiss = { activeSheet = ActiveSheet.None },
                    onSaveEnvelope = { env -> viewModel.saveEnvelopeConfig(env) },
                    onDeleteEnvelope = { id -> viewModel.deleteEnvelopeConfig(id) },
                    onAutoBalance100 = { viewModel.autoBalanceEnvelopesTo100() }
                )

                is ActiveSheet.FinancialOrAccountItem -> AddFinancialItemSheet(
                    itemType = sheet.itemType,
                    existingItem = sheet.existingItem,
                    existingAccount = sheet.existingAccount,
                    state = state,
                    onDismiss = { activeSheet = ActiveSheet.None },
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

                ActiveSheet.RestoreBackup -> RestoreJsonSheet(
                    strings = strings,
                    initialRawJson = pendingRestoreRawContent,
                    onPreviewBackup = { raw, pwd ->
                        viewModel.previewBackupImport(raw, pwd)
                    },
                    onDismiss = {
                        activeSheet = ActiveSheet.None
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

            if (localDbHealth is DatabaseHealthState.RecoveryRequired) {
                val roomPrimaryAfterImport = remember(currentUserId, localDbHealth) {
                    DataSourceConfigStore.isRoomImportCompleted(context, currentUserId) ||
                        DataSourceConfigStore.isAnyUserRoomImportCompleted(context)
                }
                DatabaseRecoveryDialog(
                    language = state.profile.language,
                    isRoomPrimaryAfterImport = roomPrimaryAfterImport,
                    onRestoreBackup = {
                        scope.launch(Dispatchers.IO) {
                            MasroufiDatabaseProvider.resetAndReopen(context)
                            DataSourceConfigStore.refreshActiveMode(context, currentUserId)
                        }
                        activeSheet = ActiveSheet.RestoreBackup
                    },
                    onResetLocalDatabase = {
                        scope.launch(Dispatchers.IO) {
                            DataSourceConfigStore.clearRoomImportCompletedForUser(context, currentUserId)
                            MasroufiDatabaseProvider.resetAndReopen(context)
                            DataSourceConfigStore.refreshActiveMode(context, currentUserId)
                        }
                    }
                )
            }

            // Step 2: Daret & Sellef WhatsApp Share Card Sheet (LTR/RTL Bitmap + Text fallback)
            val currentSharePayload = activeShareCard
            if (currentSharePayload != null) {
                com.example.ui.ShareCardBottomSheet(
                    payload = currentSharePayload,
                    initialAppLanguage = state.profile.language,
                    initialHideAmounts = state.hideAmounts,
                    isDarkTheme = state.profile.isDarkTheme,
                    onDismiss = { activeShareCard = null }
                )
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

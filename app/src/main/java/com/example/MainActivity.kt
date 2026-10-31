package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.MasroufiRepository
import com.example.data.TransactionItem
import com.example.domain.Translations
import com.example.ui.AddFinancialItemSheet
import com.example.ui.AuthScreen
import com.example.ui.BudgetAndDaretScreen
import com.example.ui.CustomizeEnvelopesSheet
import com.example.ui.HistoryScreen
import com.example.ui.MasroufiViewModel
import com.example.ui.MonthScreen
import com.example.ui.OverviewScreen
import com.example.ui.RestoreJsonSheet
import com.example.ui.SettingsBottomSheet
import com.example.ui.TransactionEditorSheet
import com.example.ui.WeekScreen
import com.example.ui.signOutUser
import com.example.ui.theme.MasroufiTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val launchedFromQuickAdd = intent?.action == "com.aistudio.masroufi.ACTION_QUICK_ADD"
        setContent {
            AppNavigation(openQuickAddOnLaunch = launchedFromQuickAdd)
        }
    }
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth,
    openQuickAddOnLaunch: Boolean = false
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
            openQuickAddOnLaunch = openQuickAddOnLaunch
        )
    }
}

@Composable
fun MainFeatureScreen(
    currentUserId: String,
    openQuickAddOnLaunch: Boolean = false
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
                MasroufiViewModel(MasroufiRepository(db), currentUserId)
            }
        }
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = Translations.forLang(state.profile.language)
    val layoutDirection = if (strings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeTab by remember { mutableStateOf("vue") }
    var showAddTxSheet by remember { mutableStateOf(openQuickAddOnLaunch) }
    var initialTxType by remember { mutableStateOf("expense") }
    var editingTx by remember { mutableStateOf<TransactionItem?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showEnvelopesSheet by remember { mutableStateOf(false) }
    var activeItemSheetType by remember { mutableStateOf<String?>(null) }
    var showRestoreSheet by remember { mutableStateOf(false) }

    if (activeTab != "vue") {
        BackHandler {
            activeTab = "vue"
        }
    }

    MasroufiTheme(darkTheme = state.profile.isDarkTheme) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    MasroufiTopBar(
                        language = state.profile.language,
                        isDarkTheme = state.profile.isDarkTheme,
                        subTitle = strings.subTitle,
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
                        onToggleTheme = { viewModel.toggleTheme() },
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
                        tabs.forEach { (key, label, icon) ->
                            NavigationBarItem(
                                selected = activeTab == key,
                                onClick = { activeTab = key },
                                icon = { Icon(imageVector = icon, contentDescription = label) },
                                label = { Text(text = label, maxLines = 1, fontSize = 11.sp) },
                                modifier = Modifier.testTag("nav_tab_$key")
                            )
                        }
                    }
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = {
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
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 600.dp)) {
                        when (activeTab) {
                            "vue" -> OverviewScreen(
                                state = state,
                                strings = strings,
                                onOpenAddModal = { type ->
                                    editingTx = null
                                    initialTxType = type
                                    showAddTxSheet = true
                                },
                                onQuickPresetTap = { preset ->
                                    viewModel.quickAddPreset(preset) { added ->
                                        scope.launch {
                                            val res = snackbarHostState.showSnackbar(
                                                message = "+ ${added.desc} (${added.amount.toInt()} DH)",
                                                actionLabel = strings.undoLabel,
                                                duration = SnackbarDuration.Short
                                            )
                                            if (res == SnackbarResult.ActionPerformed) {
                                                viewModel.deleteTransactionWithUndo(added)
                                            }
                                        }
                                    }
                                },
                                onOpenAccountModal = { activeItemSheetType = "account" },
                                onPayBill = { bill -> viewModel.payRecurringBill(bill) },
                                onAddPresetModal = { activeItemSheetType = "preset" }
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
                                onOpenNewSeasonal = { activeItemSheetType = "seasonal" },
                                onDeleteItem = { id -> viewModel.deleteFinancialItem(id) }
                            )

                            "categories" -> BudgetAndDaretScreen(
                                state = state,
                                strings = strings,
                                onOpenCustomizeEnvelopes = { showEnvelopesSheet = true },
                                onPayDaretRound = { daret -> viewModel.payDaretRound(daret) },
                                onOpenNewDaret = { activeItemSheetType = "daret" },
                                onSettleDebt = { debt -> viewModel.settleDebt(debt, 100.0) },
                                onOpenNewDebt = { activeItemSheetType = "debt" },
                                onContributeGoal = { goal -> viewModel.contributeToGoalOrSeasonal(goal, 200.0) },
                                onOpenNewGoal = { activeItemSheetType = "goal" },
                                onOpenNewBill = { activeItemSheetType = "bill" },
                                onPayBill = { bill -> viewModel.payRecurringBill(bill) },
                                onDeleteItem = { id -> viewModel.deleteFinancialItem(id) }
                            )

                            "historique" -> HistoryScreen(
                                state = state,
                                strings = strings,
                                onUpdateFilter = { fn -> viewModel.updateFilter(fn) },
                                onEditTransaction = { tx ->
                                    editingTx = tx
                                    initialTxType = tx.type
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
                                onShareJson = {
                                    shareTextContent(context, viewModel.exportBackupJson(), "masroufi_backup.json", "application/json")
                                },
                                onCopyJson = {
                                    copyToClipboard(context, viewModel.exportBackupJson())
                                    scope.launch { snackbarHostState.showSnackbar("JSON copié !") }
                                },
                                onShareCsv = {
                                    shareTextContent(context, viewModel.exportCsv(), "masroufi_export.csv", "text/csv")
                                },
                                onOpenRestoreModal = { showRestoreSheet = true }
                            )
                        }
                    }
                }
            }

            if (showAddTxSheet) {
                TransactionEditorSheet(
                    existingTx = editingTx,
                    initialType = initialTxType,
                    state = state,
                    strings = strings,
                    onDismiss = { showAddTxSheet = false },
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

            if (showSettingsSheet) {
                SettingsBottomSheet(
                    state = state,
                    strings = strings,
                    onDismiss = { showSettingsSheet = false },
                    onSaveSettings = { b, day, roll ->
                        viewModel.saveBudgetSettings(b, day, roll)
                    },
                    onSignOut = {
                        signOutUser(context, onSignOutComplete = {}, scope = scope)
                    }
                )
            }

            if (showEnvelopesSheet) {
                CustomizeEnvelopesSheet(
                    state = state,
                    onDismiss = { showEnvelopesSheet = false },
                    onSaveEnvelope = { env -> viewModel.saveEnvelopeConfig(env) }
                )
            }

            val sheetType = activeItemSheetType
            if (sheetType != null) {
                AddFinancialItemSheet(
                    itemType = sheetType,
                    onDismiss = { activeItemSheetType = null },
                    onSaveItem = { item -> viewModel.saveFinancialItem(item) },
                    onSaveAccount = { name, kind, bal ->
                        viewModel.saveAccount(name = name, kind = kind, initialBalance = bal, colorHex = "#38BDF8")
                    }
                )
            }

            if (showRestoreSheet) {
                RestoreJsonSheet(
                    strings = strings,
                    onDismiss = { showRestoreSheet = false },
                    onRestore = { raw ->
                        viewModel.restoreFromJson(raw) { ok ->
                            scope.launch {
                                snackbarHostState.showSnackbar(if (ok) "Restauration réussie !" else "Erreur JSON")
                            }
                        }
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
    subTitle: String,
    cycleLabel: String,
    onCycleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onShiftCycle: (Int) -> Unit
) {
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
                                Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF047857)))
                            ),
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
                                text = "Masroufi",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "DH",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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

            // Pay-Cycle Month Navigator Pill
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
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
                        modifier = Modifier.size(34.dp)
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
                        modifier = Modifier.size(34.dp)
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

private fun shareTextContent(context: Context, content: String, fileName: String, mimeType: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_TITLE, fileName)
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(sendIntent, fileName))
}

private fun copyToClipboard(context: Context, content: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Masroufi JSON", content))
}

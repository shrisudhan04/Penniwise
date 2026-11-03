@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.smartexpensetracker

import android.Manifest
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.widget.RemoteViews
import android.database.Cursor
import android.provider.Telephony
import android.os.Bundle
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFFF6F7FB) // Light app background
private val Panel = Color(0xFFFFFFFF) // Clean white cards
private val Purple = Color(0xFF6D4AFF) // Primary accent
private val Green = Color(0xFF159B68) // Accessible income green
private val Coral = Color(0xFFE45555) // Accessible expense red
private val Muted = Color(0xFF777B8A)
private val Ink = Color(0xFF202235) // Primary text

private val Poppins = FontFamily(
    Font(R.font.poppins_thin, FontWeight.Thin),
    Font(R.font.poppins_extralight, FontWeight.ExtraLight),
    Font(R.font.poppins_light, FontWeight.Light),
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold),
    Font(R.font.poppins_black, FontWeight.Black)
)


private val categories = listOf(
    "Food & Dining", "Shopping", "Transportation", "Bills & Utilities",
    "Entertainment", "Healthcare", "Education", "Groceries", "Rent",
    "Travel", "Investments", "Transfers", "Cash Withdrawal", "Income", "Other"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = ExpenseDatabase.get(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Purple,
                    onPrimary = Color.White,
                    secondary = Green,
                    background = Navy,
                    onBackground = Ink,
                    surface = Panel,
                    onSurface = Ink,
                    surfaceVariant = Color(0xFFEEF0F7),
                    onSurfaceVariant = Muted,
                    error = Coral
                ),
                typography = Typography(
                    displayLarge = Typography().displayLarge.copy(fontFamily = Poppins),
                    displayMedium = Typography().displayMedium.copy(fontFamily = Poppins),
                    displaySmall = Typography().displaySmall.copy(fontFamily = Poppins),
                    headlineLarge = Typography().headlineLarge.copy(fontFamily = Poppins),
                    headlineMedium = Typography().headlineMedium.copy(fontFamily = Poppins),
                    headlineSmall = Typography().headlineSmall.copy(fontFamily = Poppins),
                    titleLarge = Typography().titleLarge.copy(fontFamily = Poppins),
                    titleMedium = Typography().titleMedium.copy(fontFamily = Poppins),
                    titleSmall = Typography().titleSmall.copy(fontFamily = Poppins),
                    bodyLarge = Typography().bodyLarge.copy(fontFamily = Poppins),
                    bodyMedium = Typography().bodyMedium.copy(fontFamily = Poppins),
                    bodySmall = Typography().bodySmall.copy(fontFamily = Poppins),
                    labelLarge = Typography().labelLarge.copy(fontFamily = Poppins),
                    labelMedium = Typography().labelMedium.copy(fontFamily = Poppins),
                    labelSmall = Typography().labelSmall.copy(fontFamily = Poppins)
                )
            ) {
                TrackerApp(database, this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerApp(db: ExpenseDatabase, activity: ComponentActivity) {
    val dao = db.expenseDao()
    val budgetDao = db.budgetDao()
    val recurringDao = db.recurringDao()
    val transactions by dao.observeExpenses().collectAsState(initial = emptyList())
    val budgets by budgetDao.observeBudgets().collectAsState(initial = emptyList())
    val recurring by recurringDao.observeRecurring().collectAsState(initial = emptyList())
    val income = transactions.filter { it.transactionType == "INCOME" }.sumOf { it.amount }
    val expensesTotal = transactions.filter { it.transactionType == "EXPENSE" }.sumOf { it.amount }
    val scope = rememberCoroutineScope()

    var page by remember { mutableStateOf("Home") }
    var showEditor by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var amountText by remember { mutableStateOf("") }
    var merchantText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Other") }
    var transactionType by remember { mutableStateOf("EXPENSE") }
    var rememberMerchantRule by remember { mutableStateOf(false) }
    var applyCategoryToAll by remember { mutableStateOf(false) }
    var originalMerchantForRule by remember { mutableStateOf("") }
    var originalCategoryForRule by remember { mutableStateOf("Other") }
    var originalBankForRule by remember { mutableStateOf("") }
    var originalAccountForRule by remember { mutableStateOf("") }
    var originalSenderForRule by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var budgetAmount by remember { mutableStateOf("") }
    var budgetCategory by remember { mutableStateOf("Overall") }
    var recurringTitle by remember { mutableStateOf("") }
    var recurringAmount by remember { mutableStateOf("") }
    var detectedSms by remember { mutableStateOf<List<ExpenseEntity>>(emptyList()) }
    var showSmsReview by remember { mutableStateOf(false) }
    var smsScanning by remember { mutableStateOf(false) }
    var showScanDaysDialog by remember { mutableStateOf(false) }
    var selectedScanDays by remember { mutableIntStateOf(7) }
    var smsEnabled by remember {
        mutableStateOf(activity.getSharedPreferences("settings", 0).getBoolean("sms_tracking", false))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(activity, "Notifications are disabled. You can enable them in Android Settings.", Toast.LENGTH_LONG).show()
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.RECEIVE_SMS] == true &&
                results[Manifest.permission.READ_SMS] == true
        smsEnabled = granted
        activity.getSharedPreferences("settings", 0).edit {
            putBoolean("sms_tracking", granted)
        }
        Toast.makeText(activity, if (granted) "SMS tracking enabled" else "SMS permissions denied", Toast.LENGTH_LONG).show()
        if (granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun openAdd() {
        editingId = null
        amountText = ""
        merchantText = ""
        category = "Other"
        transactionType = "EXPENSE"
        rememberMerchantRule = false
        applyCategoryToAll = false
        originalMerchantForRule = ""
        originalCategoryForRule = "Other"
        originalBankForRule = ""
        originalAccountForRule = ""
        originalSenderForRule = ""
        showEditor = true
    }

    fun startSmsScan(days: Int) {
        scope.launch {
            smsScanning = true
            try {
                val existingHashes = transactions.mapNotNull {
                    it.smsHash.takeIf(String::isNotBlank)
                }.toSet()

                detectedSms = scanRecentSms(
                    activity = activity,
                    existingHashes = existingHashes,
                    lookbackDays = days,
                    reprocessExisting = false
                )
                showSmsReview = true

                if (detectedSms.isEmpty()) {
                    Toast.makeText(
                        activity,
                        "No new eligible debit or credit SMS found in the last $days days",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (_: SecurityException) {
                Toast.makeText(activity, "SMS permission is required", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                Toast.makeText(activity, "SMS scan failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                smsScanning = false
            }
        }
    }

    Scaffold(
        containerColor = Navy,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .background(Color(0xFF0F1F4B), RoundedCornerShape(28.dp))
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Home", "Transactions", "Analytics", "Budgets").forEach { destination ->
                        val icon = when (destination) {
                            "Home"         -> Icons.Default.Home
                            "Transactions" -> Icons.AutoMirrored.Filled.ReceiptLong
                            "Analytics"    -> Icons.Default.BarChart
                            else           -> Icons.Default.AccountBalanceWallet
                        }
                        val selected = page == destination
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 9.dp, horizontal = 6.dp)
                                .background(
                                    color = if (selected) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { page = destination },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = destination,
                                tint = if (selected) Color(0xFF0F1F4B) else Color.White.copy(alpha = 0.38f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            when (page) {
                "Home" -> DashboardPage(
                    transactions, income, expensesTotal, budgets,
                    onNavigate = { page = it },
                    onRefreshSms = {
                        if (!smsEnabled) {
                            Toast.makeText(activity, "Enable SMS permission first", Toast.LENGTH_LONG).show()
                        } else {
                            showScanDaysDialog = true
                        }
                    },
                    onEdit = { item ->
                        editingId = item.id
                        amountText = item.amount.toString()
                        merchantText = item.merchant
                        category = item.category
                        transactionType = item.transactionType
                        originalMerchantForRule = item.merchant
                        originalCategoryForRule = item.category
                        originalBankForRule = item.bankName
                        originalAccountForRule = item.accountReference
                        originalSenderForRule = item.smsSender
                        rememberMerchantRule = item.smsHash.isNotBlank() && !item.manuallyAdded
                        applyCategoryToAll = false
                        showEditor = true
                    }
                )
                "Transactions" -> TransactionsPage(
                    transactions, search, context = activity, onSearch = { search = it },
                    onDelete = { id -> scope.launch { dao.delete(id) } },
                    onDeleteAll = {
                        scope.launch {
                            dao.deleteAll()
                            search = ""
                            Toast.makeText(activity, "All transactions deleted", Toast.LENGTH_LONG).show()
                        }
                    },
                    onEdit = { item ->
                        editingId = item.id
                        amountText = item.amount.toString()
                        merchantText = item.merchant
                        category = item.category
                        transactionType = item.transactionType
                        originalMerchantForRule = item.merchant
                        originalCategoryForRule = item.category
                        originalBankForRule = item.bankName
                        originalAccountForRule = item.accountReference
                        originalSenderForRule = item.smsSender
                        rememberMerchantRule = item.smsHash.isNotBlank() && !item.manuallyAdded
                        applyCategoryToAll = false
                        showEditor = true
                    }
                )
                "Analytics" -> AnalyticsPage(transactions, income, expensesTotal)
                "Budgets" -> BudgetsPage(
                    budgets, transactions, budgetAmount, { budgetAmount = it },
                    budgetCategory, { budgetCategory = it },
                    onSave = {
                        val value = budgetAmount.toDoubleOrNull()
                        if (value != null && value > 0) scope.launch {
                            val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
                            budgetDao.insert(BudgetEntity(category = budgetCategory, amount = value, monthKey = month))
                            budgetAmount = ""
                        }
                    },
                    onDelete = { id -> scope.launch { budgetDao.delete(id) } }
                )
                "Categories" -> CategoriesPage(transactions)
                "Merchants" -> MerchantsPage(transactions)
                "Recurring" -> RecurringPage(
                    recurring, recurringTitle, { recurringTitle = it },
                    recurringAmount, { recurringAmount = it },
                    onSave = {
                        val value = recurringAmount.toDoubleOrNull()
                        if (value != null && value > 0 && recurringTitle.isNotBlank()) scope.launch {
                            recurringDao.insert(
                                RecurringEntity(
                                    title = recurringTitle.trim(),
                                    amount = value,
                                    category = SmsParser.classify(recurringTitle, ""),
                                    frequency = "Monthly"
                                )
                            )
                            recurringTitle = ""
                            recurringAmount = ""
                        }
                    },
                    onDelete = { id -> scope.launch { recurringDao.delete(id) } }
                )
                "SMS Import" -> SmsPage(
                    enabled = smsEnabled,
                    onScan = {
                        if (!smsEnabled) {
                            Toast.makeText(activity, "Enable SMS permission first", Toast.LENGTH_LONG).show()
                        } else {
                            showScanDaysDialog = true
                        }
                    },
                    onToggle = {
                        if (!smsEnabled) {
                            smsPermissionLauncher.launch(
                                arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                            )
                        } else {
                            smsEnabled = false
                            activity.getSharedPreferences("settings", 0).edit {
                                putBoolean("sms_tracking", false)
                            }
                        }
                    })
                "Settings" -> SettingsPage(
                    smsEnabled,
                    onSms = { page = "SMS Import" },
                    onCsv = { exportCsv(activity, transactions) }
                )
                else -> DashboardPage(
                    transactions, income, expensesTotal, budgets,
                    onNavigate = { page = it },
                    onRefreshSms = {},
                    onEdit = { item ->
                        editingId = item.id
                        amountText = item.amount.toString()
                        merchantText = item.merchant
                        category = item.category
                        transactionType = item.transactionType
                        originalMerchantForRule = item.merchant
                        originalCategoryForRule = item.category
                        originalBankForRule = item.bankName
                        originalAccountForRule = item.accountReference
                        originalSenderForRule = item.smsSender
                        rememberMerchantRule = item.smsHash.isNotBlank() && !item.manuallyAdded
                        applyCategoryToAll = false
                        showEditor = true
                    }
                )
            }
        }
    }

    if (showScanDaysDialog) {
        AlertDialog(
            onDismissRequest = { showScanDaysDialog = false },
            title = { Text("Scan SMS history") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose how many previous days to recheck in your SMS inbox.")
                    listOf(1, 3, 7, 15, 30, 90).forEach { days ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedScanDays == days,
                                onClick = { selectedScanDays = days }
                            )
                            Text(
                                text = "Last $days ${if (days == 1) "day" else "days"}",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val days = selectedScanDays
                    showScanDaysDialog = false
                    startSmsScan(days)
                }) { Text("Scan") }
            },
            dismissButton = {
                TextButton(onClick = { showScanDaysDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSmsReview) {
        AlertDialog(
            onDismissRequest = { showSmsReview = false },
            title = { Text("Review detected transactions (${detectedSms.size})") },
            text = {
                if (detectedSms.isEmpty()) {
                    Text("No new eligible debit or credit transactions were found in the selected period.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(detectedSms, key = { it.smsHash }) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.merchant, color = Ink, fontWeight = FontWeight.SemiBold)
                                    Text("${item.category} · ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(item.transactionDate))}", color = Muted, fontSize = 12.sp)
                                }
                                Text((if (item.transactionType == "INCOME") "+₹" else "−₹") + money(item.amount), color = if (item.transactionType == "INCOME") Green else Coral, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = Muted.copy(alpha = 0.2f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = detectedSms.isNotEmpty(), onClick = {
                    scope.launch {
                        var saved = 0
                        detectedSms.forEach { item ->
                            val result = dao.insert(item)
                            if (result != -1L) saved++
                        }
                        detectedSms = emptyList()
                        showSmsReview = false
                        Toast.makeText(activity, "$saved transactions added", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Add All") }
            },
            dismissButton = {
                TextButton(onClick = { showSmsReview = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            shape = RoundedCornerShape(26.dp),
            containerColor = Color.White,
            titleContentColor = Color(0xFF0B1F3A),
            textContentColor = Color(0xFF202235),
            title = {
                Text(
                    text = if (editingId == null) "Add transaction" else "Edit transaction",
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF0B1F3A)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0B1F3A),
                            unfocusedBorderColor = Color(0xFFD7DCE5),
                            focusedLabelColor = Color(0xFF0B1F3A),
                            unfocusedLabelColor = Color(0xFF7A8495),
                            focusedTextColor = Color(0xFF182338),
                            unfocusedTextColor = Color(0xFF182338),
                            cursorColor = Color(0xFF0B1F3A)
                        )
                    )
                    OutlinedTextField(
                        value = merchantText,
                        onValueChange = { merchantText = it },
                        label = { Text("Merchant / receiver") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0B1F3A),
                            unfocusedBorderColor = Color(0xFFD7DCE5),
                            focusedLabelColor = Color(0xFF0B1F3A),
                            unfocusedLabelColor = Color(0xFF7A8495),
                            focusedTextColor = Color(0xFF182338),
                            unfocusedTextColor = Color(0xFF182338),
                            cursorColor = Color(0xFF0B1F3A)
                        )
                    )
                    if (editingId != null && originalSenderForRule.isNotBlank() && originalMerchantForRule.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F7FA)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Checkbox(
                                checked = rememberMerchantRule,
                                onCheckedChange = { rememberMerchantRule = it }
                            )
                            Text("Remember merchant for matching SMS", color = Ink, fontFamily = Poppins, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = transactionType == "EXPENSE",
                            onClick = { transactionType = "EXPENSE" },
                            label = {
                                Text(
                                    "Expense",
                                    fontFamily = Poppins,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (transactionType == "EXPENSE") Color.White else Color(0xFF0B1F3A)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0B1F3A),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = Color(0xFF0B1F3A)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = transactionType == "EXPENSE",
                                borderColor = Color(0xFF0B1F3A),
                                selectedBorderColor = Color(0xFF0B1F3A)
                            )
                        )
                        FilterChip(
                            selected = transactionType == "INCOME",
                            onClick = { transactionType = "INCOME" },
                            label = {
                                Text(
                                    "Income",
                                    fontFamily = Poppins,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (transactionType == "INCOME") Color.White else Color(0xFF0B1F3A)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0B1F3A),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = Color(0xFF0B1F3A)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = transactionType == "INCOME",
                                borderColor = Color(0xFF0B1F3A),
                                selectedBorderColor = Color(0xFF0B1F3A)
                            )
                        )
                    }
                    var expanded by remember { mutableStateOf(false) }
                    var categoryInput by remember { mutableStateOf(category) }
                    val categoryFocusRequester = remember { FocusRequester() }
                    val categoryKeyboardController = LocalSoftwareKeyboardController.current
                    val paleViolet = Color(0xFFF2EEFF)
                    val selectedViolet = Color(0xFFE5DCFF)
                    val violetAccent = Color(0xFF7C5CDB)

                    // The category input itself is searchable; no separate search bar is shown.
                    LaunchedEffect(showEditor, editingId) {
                        categoryInput = category
                    }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { isExpanded ->
                            expanded = isExpanded
                            if (isExpanded) {
                                categoryInput = category
                            }
                        }
                    ) {
                        OutlinedTextField(
                            value = categoryInput,
                            onValueChange = { typed ->
                                categoryInput = typed
                                expanded = true
                            },
                            label = {
                                Text(
                                    "Category",
                                    fontFamily = Poppins,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(
                                    type = MenuAnchorType.PrimaryEditable,
                                    enabled = true
                                )
                                .focusRequester(categoryFocusRequester)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) expanded = true
                                },
                            shape = RoundedCornerShape(18.dp),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = violetAccent,
                                unfocusedBorderColor = Color(0xFFD8D1EA),
                                focusedLabelColor = violetAccent,
                                unfocusedLabelColor = Muted,
                                focusedTextColor = Ink,
                                unfocusedTextColor = Ink,
                                cursorColor = violetAccent
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = Poppins,
                                fontSize = 13.sp,
                                color = Ink
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Search
                            )
                        )

                        val filteredCategories = categories.filter {
                            it.contains(categoryInput.trim(), ignoreCase = true)
                        }

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .exposedDropdownSize(matchTextFieldWidth = true)
                                .clip(RoundedCornerShape(36.dp))
                                .background(paleViolet)
                                .heightIn(max = 300.dp)
                                .padding(5.dp)
                        ) {
                            filteredCategories.forEach { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 3.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(
                                            if (category.equals(option, ignoreCase = true))
                                                selectedViolet else Color.Transparent
                                        )
                                        .clickable {
                                            category = option
                                            categoryInput = option
                                            expanded = false
                                            categoryKeyboardController?.hide()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = option,
                                        fontFamily = Poppins,
                                        fontWeight = if (category.equals(option, ignoreCase = true))
                                            FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                        color = Ink,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (category.equals(option, ignoreCase = true)) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = violetAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            val typedCategory = categoryInput.trim()
                            if (typedCategory.isNotEmpty() &&
                                categories.none { it.equals(typedCategory, ignoreCase = true) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 3.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color(0xFFEDE5FF))
                                        .clickable {
                                            category = typedCategory
                                            categoryInput = typedCategory
                                            expanded = false
                                            categoryKeyboardController?.hide()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add category",
                                        tint = violetAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(7.dp))
                                    Text(
                                        text = "Add '$typedCategory'",
                                        fontFamily = Poppins,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = violetAccent
                                    )
                                }
                            }
                        }
                    }
                    if (editingId != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F7FA)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Checkbox(
                                checked = applyCategoryToAll,
                                onCheckedChange = { applyCategoryToAll = it }
                            )
                            Text(
                                "Apply category only to this receiver",
                                color = Ink,
                                fontFamily = Poppins,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsedAmount = amountText.toDoubleOrNull()
                    if (parsedAmount != null && parsedAmount > 0 && merchantText.isNotBlank()) {
                        scope.launch {
                            if (editingId == null) {
                                dao.insert(
                                    ExpenseEntity(
                                        amount = parsedAmount,
                                        merchant = merchantText.trim(),
                                        receiver = merchantText.trim(),
                                        category = category,
                                        transactionType = transactionType,
                                        manuallyAdded = true
                                    )
                                )
                            } else {
                                val old = dao.getById(editingId!!)
                                if (old != null) {
                                    dao.update(
                                        old.copy(
                                            amount = parsedAmount,
                                            merchant = merchantText.trim(),
                                            receiver = merchantText.trim(),
                                            category = category,
                                            transactionType = transactionType
                                        )
                                    )
                                }
                            }
                            if (applyCategoryToAll && editingId != null) {
                                // Apply the selected category only to transactions from this same receiver/merchant.
                                val editedTransaction = dao.getById(editingId!!)
                                val targetReceiver = (editedTransaction?.receiver?.takeIf { it.isNotBlank() }
                                    ?: editedTransaction?.merchant.orEmpty()).trim()

                                if (targetReceiver.isNotBlank()) {
                                    transactions.forEach { transaction ->
                                        val transactionReceiver = (transaction.receiver.takeIf { it.isNotBlank() }
                                            ?: transaction.merchant).trim()
                                        if (transactionReceiver.equals(targetReceiver, ignoreCase = true) &&
                                            transaction.category != category
                                        ) {
                                            dao.update(transaction.copy(category = category))
                                        }
                                    }
                                }
                            }
                            if (rememberMerchantRule && originalMerchantForRule.isNotBlank()) {
                                saveMerchantLearningRule(
                                    activity = activity,
                                    bankName = originalBankForRule,
                                    accountReference = originalAccountForRule,
                                    smsSender = originalSenderForRule,
                                    originalDescriptor = originalMerchantForRule,
                                    learnedMerchant = merchantText.trim(),
                                    learnedCategory = category
                                )
                                Toast.makeText(activity, "Merchant learning rule saved", Toast.LENGTH_SHORT).show()
                            }
                            showEditor = false
                            editingId = null
                        }
                    } else {
                        Toast.makeText(activity, "Enter a valid amount and merchant", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Save", fontFamily = Poppins, fontWeight = FontWeight.SemiBold, color = Color(0xFF0B1F3A)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("Cancel", fontFamily = Poppins, fontWeight = FontWeight.Medium, color = Color(0xFF667085)) }
            }
        )
    }
}

@Composable
private fun DashboardPage(
    items: List<ExpenseEntity>,
    income: Double,
    total: Double,
    budgets: List<BudgetEntity>,
    onNavigate: (String) -> Unit,
    onRefreshSms: () -> Unit,
    onEdit: (ExpenseEntity) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Good day 👋", color = Muted, fontSize = 13.sp)
                    Text("Your finances", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = onRefreshSms,
                    enabled = true,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFFEDE7FF), RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh SMS from the past 24 hours",
                        tint = Purple
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("NET CASH FLOW", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("₹${money(income - total)}", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryMetric("Income", income, Green)
                        SummaryMetric("Expenses", total, Coral)
                        SummaryMetric("Savings", income - total, Purple)
                    }
                }
            }
        }
        item { SectionTitle("Spending trend") }
        item { TrendBars(items) }
        item { SectionTitle("Budget progress") }
        if (budgets.isEmpty()) item { Text("No budgets yet. Open Budgets to create one.", color = Muted) }
        items(budgets.take(4)) { budget ->
            val spent = items.filter {
                it.transactionType == "EXPENSE" &&
                        (budget.category == "Overall" || it.category == budget.category)
            }.sumOf { it.amount }
            BudgetProgress(budget.category, spent, budget.amount)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = null,
                            tint = Purple
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Auto-detect expenses from SMS",
                                color = Ink,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Scan bank and UPI messages from a selected date range",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Button(
                        onClick = { onNavigate("SMS Import") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan SMS & Detect Expenses")
                    }
                }
            }
        }
        item { SectionTitle("Recent transactions") }
        items(items.take(5)) { transaction ->
            TransactionRow(transaction, onClick = { onEdit(transaction) })
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: Double, color: Color) {
    Column {
        Text(label, color = Muted, fontSize = 12.sp)
        Text("₹${money(value)}", color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}


@Composable
private fun TransactionsPage(
    items: List<ExpenseEntity>,
    search: String,
    context: Context,
    onSearch: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteAll: () -> Unit,
    onEdit: (ExpenseEntity) -> Unit
) {
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var quickCategory by remember { mutableStateOf("All") }
    var filterCategory by remember { mutableStateOf("All") }
    var filterType by remember { mutableStateOf("All") }
    var filterFromDate by remember { mutableStateOf<Long?>(null) }
    var filterToDate by remember { mutableStateOf<Long?>(null) }
    var datePickerTarget by remember { mutableStateOf<String?>(null) }

    val calendar = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
    }
    val monthStart = calendar.timeInMillis
    val monthExpenses = items.filter {
        it.transactionType == "EXPENSE" && it.transactionDate >= monthStart
    }.sumOf { it.amount }
    val monthIncome = items.filter {
        it.transactionType == "INCOME" && it.transactionDate >= monthStart
    }.sumOf { it.amount }

    val filtered: List<ExpenseEntity> = items
        .filter { txn ->
            val query = search.trim()
            query.isEmpty() ||
                    txn.merchant.contains(query, ignoreCase = true) ||
                    txn.category.contains(query, ignoreCase = true) ||
                    txn.bankName.contains(query, ignoreCase = true) ||
                    txn.transactionType.contains(query, ignoreCase = true) ||
                    money(txn.amount).contains(query, ignoreCase = true)
        }
        .filter { txn ->
            quickCategory == "All" ||
                    txn.category.equals(quickCategory, ignoreCase = true)
        }
        .filter { txn ->
            filterCategory == "All" || txn.category.equals(filterCategory, ignoreCase = true)
        }
        .filter { txn ->
            filterType == "All" || txn.transactionType.equals(filterType, ignoreCase = true)
        }
        .filter { txn ->
            val from = filterFromDate
            val to = filterToDate
            (from == null || txn.transactionDate >= from) &&
                    (to == null || txn.transactionDate < to + 86_400_000L)
        }
        .sortedByDescending { it.transactionDate }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item(key = "title_row") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (selectionMode) "${selectedIds.size} selected" else "Transactions",
                    color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    fontFamily = Poppins
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectionMode) {
                        TextButton(onClick = {
                            selectionMode = false
                            selectedIds = emptySet()
                        }) { Text("Cancel", color = Purple) }
                    }
                    TextButton(
                        onClick = {
                            val now = System.currentTimeMillis()
                            val testItem = ExpenseEntity(
                                amount = 2700.0,
                                merchant = "Test Salary",
                                receiver = "Salary credit",
                                category = "Income",
                                transactionType = "INCOME",
                                paymentMethod = "NEFT",
                                bankName = "TEST BANK",
                                transactionDate = now,
                                smsHash = "test_notification_$now",
                                notes = "Test notification",
                                manuallyAdded = true
                            )
                            showTransactionNotification(context, testItem)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Green)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(3.dp))
                        Text("Test")
                    }
                    TextButton(
                        onClick = { showDeleteAllDialog = true },
                        enabled = items.isNotEmpty(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Coral)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(3.dp))
                        Text("Delete All")
                    }
                }
            }
        }

        item(key = "summary_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2D63)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                "Total spent · ${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date())}",
                                color = Color(0xFFAAB8D8), fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold, fontFamily = Poppins
                            )
                            Text(
                                "₹${money(monthExpenses)}", color = Color.White,
                                fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Poppins
                            )
                        }
                        Surface(color = Color(0xFF33477D), shape = RoundedCornerShape(14.dp)) {
                            Icon(
                                Icons.Default.Notifications, contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(12.dp).size(20.dp)
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Color(0xFF142452), RoundedCornerShape(14.dp))
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("₹${money(monthIncome)}", color = Color(0xFF35D980),
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Poppins)
                            Text("Income", color = Color(0xFFAAB8D8), fontSize = 11.sp, fontFamily = Poppins)
                        }
                        Box(Modifier.width(1.dp).height(38.dp).background(Color(0xFF34446E)))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("₹${money(monthIncome - monthExpenses)}", color = Color.White,
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Poppins)
                            Text("Balance", color = Color(0xFFAAB8D8), fontSize = 11.sp, fontFamily = Poppins)
                        }
                        Box(Modifier.width(1.dp).height(38.dp).background(Color(0xFF34446E)))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val topSpend = items.filter {
                                it.transactionType == "EXPENSE" && it.transactionDate >= monthStart
                            }.groupBy { it.category }.values.maxOfOrNull { group ->
                                group.sumOf { txn -> txn.amount }
                            } ?: 0.0
                            Text("₹${money(topSpend)}", color = Color(0xFFFF6B72),
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Poppins)
                            Text("Top spend", color = Color(0xFFAAB8D8), fontSize = 11.sp, fontFamily = Poppins)
                        }
                    }
                }
            }
        }

        item(key = "filter_chips") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (listOf("All") + categories).distinct().forEach { quick ->
                    FilterChip(
                        selected = quickCategory == quick,
                        onClick = { quickCategory = quick },
                        label = {
                            Text(quick, fontSize = 12.sp, fontFamily = Poppins,
                                fontWeight = FontWeight.SemiBold, maxLines = 1)
                        },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1B2D63),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color(0xFF4E5D7E)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = quickCategory == quick,
                            borderColor = Color(0xFFD2D9E8),
                            selectedBorderColor = Color(0xFF1B2D63)
                        )
                    )
                }
            }
        }

        item(key = "search_row") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).height(42.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color(0xFFD9DCE7)), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null,
                        tint = Muted, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Box(Modifier.weight(1f)) {
                        if (search.isEmpty()) Text("Search....", color = Muted, fontSize = 12.sp,
                            fontWeight = FontWeight.Normal, fontFamily = Poppins)
                        BasicTextField(
                            value = search, onValueChange = onSearch, singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Ink, fontFamily = Poppins,
                                fontSize = 12.sp, fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { onSearch("") }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null,
                                tint = Muted, modifier = Modifier.size(15.dp))
                        }
                    }
                }
                Box(
                    modifier = Modifier.size(42.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color(0xFFD9DCE7)), RoundedCornerShape(12.dp))
                        .clickable { showFilterSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filter transactions",
                        tint = Purple, modifier = Modifier.size(21.dp))
                }
            }
        }

        if (selectionMode) {
            item(key = "bulk_row") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        selectedIds = if (selectedIds.size == filtered.size)
                            emptySet() else filtered.map { txn -> txn.id }.toSet()
                    }) {
                        Text(if (selectedIds.size == filtered.size) "Deselect all" else "Select all")
                    }
                    TextButton(
                        enabled = selectedIds.isNotEmpty(),
                        onClick = { showBulkDeleteDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Coral)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text("Delete selected")
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item(key = "empty_state") {
                Box(Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null,
                            tint = Muted, modifier = Modifier.size(42.dp))
                        Text("No transactions found", color = Muted, fontFamily = Poppins)
                        Text("Try changing your search or filters", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            val grouped = filtered.groupBy { txn ->
                SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(txn.transactionDate))
            }
            grouped.forEach { (dateLabel, dayItems) ->
                item(key = "header_$dateLabel") {
                    Text(
                        dateLabel.uppercase(Locale.getDefault()),
                        color = Color(0xFF8995AE), fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 5.dp, start = 4.dp),
                        fontFamily = Poppins
                    )
                }
                dayItems.forEach { txn ->
                    item(key = "transaction_${txn.id}") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0E6F0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            if (selectionMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = txn.id in selectedIds,
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) selectedIds + txn.id
                                            else selectedIds - txn.id
                                        }
                                    )
                                    Box(Modifier.weight(1f)) {
                                        TransactionRow(
                                            txn,
                                            inGroupedCard = true,
                                            onClick = {
                                                selectedIds = if (txn.id in selectedIds)
                                                    selectedIds - txn.id else selectedIds + txn.id
                                            },
                                            onLongClick = {}
                                        )
                                    }
                                }
                            } else {
                                TransactionRow(
                                    txn,
                                    inGroupedCard = true,
                                    onClick = { onEdit(txn) },
                                    onLongClick = {
                                        selectionMode = true
                                        selectedIds = selectedIds + txn.id
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete all transactions?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAllDialog = false
                    onDeleteAll()
                    selectionMode = false
                    selectedIds = emptySet()
                }) { Text("Delete all", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Delete selected transactions?") },
            text = { Text("Delete ${selectedIds.size} selected transaction(s)?") },
            confirmButton = {
                TextButton(onClick = {
                    selectedIds.toList().forEach(onDelete)
                    selectedIds = emptySet()
                    selectionMode = false
                    showBulkDeleteDialog = false
                }) { Text("Delete", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showFilterSheet) {
        // Render Filter Transactions as a centered standard dialog (not a bottom sheet).
        // Dialog stays compact and does not expand when the user swipes.
        val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
        val filterPopupHeight = (screenHeightDp * 0.70f).coerceAtMost(620.dp)
        Dialog(onDismissRequest = { showFilterSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .height(filterPopupHeight)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFFF7F3FC))
                    .padding(top = 16.dp)
            ) {
                var categoryMenuExpanded by remember { mutableStateOf(false) }
                var filterCategorySearch by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp)
                ) {
                    // Scrollable filter controls stay within the available sheet height.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Filter transactions",
                            fontFamily = Poppins,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )

                        Text(
                            text = "Transaction type",
                            fontFamily = Poppins,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("All", "INCOME", "EXPENSE").forEach { type ->
                                val selected = filterType == type
                                FilterChip(
                                    selected = selected,
                                    onClick = { filterType = type },
                                    label = {
                                        Text(
                                            text = if (type == "All") "All" else type.lowercase().replaceFirstChar { ch -> ch.uppercase() },
                                            fontFamily = Poppins,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF25205A),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color.White,
                                        labelColor = Ink
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = Color(0xFFD8CFF0),
                                        selectedBorderColor = Color(0xFF25205A)
                                    )
                                )
                            }
                        }

                        Text(
                            text = "Date range",
                            fontFamily = Poppins,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf("From date" to filterFromDate, "To date" to filterToDate).forEach { (label, selectedDate) ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    // Date label stays outside the input field for a cleaner form layout.
                                    Text(
                                        text = label,
                                        fontFamily = Poppins,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Ink
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(Color.White)
                                            .border(1.dp, Color(0xFFD8CFF0), RoundedCornerShape(24.dp))
                                            .clickable {
                                                datePickerTarget = if (label == "From date") "from" else "to"
                                            }
                                            .padding(horizontal = 12.dp, vertical = 13.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            selectedDate?.let { formatFilterDate(it) } ?: "Select date",
                                            fontFamily = Poppins,
                                            fontSize = 12.sp,
                                            color = Ink,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            Icons.Default.CalendarMonth,
                                            contentDescription = "Choose $label",
                                            tint = Color(0xFF6D4AFF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Category",
                            fontFamily = Poppins,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )

                        ExposedDropdownMenuBox(
                            expanded = categoryMenuExpanded,
                            onExpandedChange = {
                                categoryMenuExpanded = it
                                if (it) filterCategorySearch = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = if (categoryMenuExpanded) filterCategorySearch else filterCategory,
                                onValueChange = { typed ->
                                    filterCategorySearch = typed
                                    categoryMenuExpanded = true
                                },
                                readOnly = false,
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded)
                                },
                                shape = RoundedCornerShape(26.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF8E78D8),
                                    unfocusedBorderColor = Color(0xFFD8CFF0),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedTextColor = Ink,
                                    unfocusedTextColor = Ink
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = Poppins,
                                    fontSize = 13.sp
                                )
                            )

                            ExposedDropdownMenu(
                                expanded = categoryMenuExpanded,
                                onDismissRequest = { categoryMenuExpanded = false },
                                modifier = Modifier
                                    .heightIn(max = 280.dp)
                                    .clip(RoundedCornerShape(30.dp))
                                    .background(Color(0xFFF1EBFF))
                                    .padding(6.dp)
                            ) {
                                val matchingCategories = (listOf("All") + categories).distinct().filter {
                                    it.contains(filterCategorySearch, ignoreCase = true)
                                }
                                matchingCategories.forEach { cat ->
                                    val selected = filterCategory == cat
                                    DropdownMenuItem(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(
                                                if (selected) Color(0xFFE2D8FF)
                                                else Color.Transparent
                                            ),
                                        text = {
                                            Text(
                                                text = cat,
                                                fontFamily = Poppins,
                                                fontSize = 13.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = Ink
                                            )
                                        },
                                        onClick = {
                                            filterCategory = cat
                                            filterCategorySearch = ""
                                            categoryMenuExpanded = false
                                        },
                                        trailingIcon = if (selected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF6D4AFF)) }
                                        } else null,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                    } // End of scrollable filter controls

                    // Keep Reset and Apply visible at the bottom of the sheet.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            filterCategory = "All"
                            filterType = "All"
                            filterFromDate = null
                            filterToDate = null
                            quickCategory = "All"
                            showFilterSheet = false
                        }) {
                            Text("Reset", fontFamily = Poppins, color = Color(0xFF6D4AFF))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { showFilterSheet = false },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25205A))
                        ) {
                            Text("Apply", fontFamily = Poppins, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    val filterDateToastContext = androidx.compose.ui.platform.LocalContext.current

    datePickerTarget?.let { target ->
        val initialDate = if (target == "from") filterFromDate else filterToDate
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate)
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { picked ->
                        // Compare calendar days in UTC (the date picker stores UTC day millis).
                        val todayUtc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }.timeInMillis

                        when (target) {
                            "from" -> {
                                when {
                                    picked > todayUtc -> Toast.makeText(
                                        filterDateToastContext,
                                        "From date cannot be in the future",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    filterToDate != null && picked >= filterToDate!! -> Toast.makeText(
                                        filterDateToastContext,
                                        "From date must be earlier than To date",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    else -> {
                                        filterFromDate = picked
                                        datePickerTarget = null
                                    }
                                }
                            }
                            "to" -> {
                                if (filterFromDate != null && picked <= filterFromDate!!) {
                                    Toast.makeText(
                                        filterDateToastContext,
                                        "To date must be later than From date",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    filterToDate = picked
                                    datePickerTarget = null
                                }
                            }
                        }
                    } ?: run { datePickerTarget = null }
                }) { Text("OK", fontFamily = Poppins, color = Color(0xFF6D4AFF)) }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTarget = null }) {
                    Text("Cancel", fontFamily = Poppins, color = Muted)
                }
            },
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = Color(0xFF25205A),
                    selectedDayContentColor = Color.White,
                    todayDateBorderColor = Color(0xFF6D4AFF)
                )
            )
        }
    }
}

private fun formatFilterDate(dateMillis: Long): String {
    return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(Date(dateMillis))
}

@Composable
private fun TransactionRow(
    item: ExpenseEntity,
    inGroupedCard: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isIncome = item.transactionType == "INCOME"
    val accent = when {
        isIncome -> Green
        item.category.contains("Food", ignoreCase = true) -> Color(0xFFE99A36)
        item.category.contains("Transport", ignoreCase = true) -> Color(0xFF4388E8)
        item.category.contains("Shopping", ignoreCase = true) -> Color(0xFF9B68E8)
        item.category.contains("Bill", ignoreCase = true) -> Color(0xFF27A6A0)
        item.category.contains("Health", ignoreCase = true) -> Color(0xFFE36A86)
        else -> Purple
    }

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = accent.copy(alpha = 0.10f), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            isIncome -> Icons.Default.SouthWest
                            item.category.contains("Food", ignoreCase = true) -> Icons.Default.Restaurant
                            item.category.contains("Transport", ignoreCase = true) -> Icons.Default.DirectionsCar
                            item.category.contains("Shopping", ignoreCase = true) -> Icons.Default.ShoppingBag
                            item.category.contains("Bill", ignoreCase = true) -> Icons.Default.Receipt
                            item.category.contains("Health", ignoreCase = true) -> Icons.Default.MedicalServices
                            else -> Icons.Default.ReceiptLong
                        },
                        contentDescription = null, tint = accent, modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.receiver.ifBlank { item.merchant }, color = Ink, fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Text((if (isIncome) "+" else "−") + "₹${money(item.amount)}",
                color = if (isIncome) Green else Coral, fontFamily = Poppins,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1,
                modifier = Modifier.padding(start = 2.dp))
        }
    }

    if (inGroupedCard) {
        Box(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
            rowContent()
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFECEEF5))
        ) { rowContent() }
    }
}

@Composable
private fun AnalyticsPage(items: List<ExpenseEntity>, income: Double, total: Double) {
    val groups = items.filter { it.transactionType == "EXPENSE" }
        .groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
        item { Text("Analytics", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Income vs Expenses", color = Muted)
                    Spacer(Modifier.height(8.dp))
                    SummaryMetric("Income", income, Green)
                    SummaryMetric("Expenses", total, Coral)
                    SummaryMetric("Savings rate", if (income > 0) ((income - total) / income) * 100 else 0.0, Purple)
                }
            }
        }
        item { Text("Category breakdown", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        items(groups) { (categoryName, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(categoryName, color = Ink)
                    Text("₹${money(value)}", color = Muted)
                }
                LinearProgressIndicator(
                    progress = { if (total > 0) (value / total).toFloat().coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Purple,
                    trackColor = Panel
                )
            }
        }
        item { Text("Recent spending trend", color = Ink, fontWeight = FontWeight.Bold) }
        item { TrendBars(items) }
    }
}

@Composable
private fun TrendBars(items: List<ExpenseEntity>) {
    val dayGroups = items
        .filter { it.transactionType == "EXPENSE" }
        .groupBy { SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(it.transactionDate)) }
        .entries
        .take(7)
        .toList()
        .reversed()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp)
    ) {
        if (dayGroups.isEmpty()) {
            Text(
                "Add expenses to see your trend chart.",
                color = Muted,
                modifier = Modifier.padding(18.dp)
            )
        } else {
            val maxValue = dayGroups.maxOfOrNull { entry ->
                entry.value.sumOf { it.amount }
            }?.coerceAtLeast(1.0) ?: 1.0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                dayGroups.forEach { (day, records) ->
                    val value = records.sumOf { it.amount }

                    // Square-root scaling makes smaller amounts visible while retaining
                    // a clear visual distinction. The highest amount always reaches
                    // the chart's maximum bar height.
                    val normalized = (value / maxValue).toFloat().coerceIn(0f, 1f)
                    val targetHeight = if (value <= 0.0) 0f else {
                        (72f * kotlin.math.sqrt(normalized)).coerceIn(8f, 72f)
                    }

                    val animatedHeight by androidx.compose.animation.core.animateDpAsState(
                        targetValue = targetHeight.dp,
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 800,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                        ),
                        label = "expenseBarHeight-$day"
                    )

                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = money(value),
                            color = Muted,
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Normal,
                            fontSize = 8.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(5.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(animatedHeight)
                                .background(
                                    Purple,
                                    RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)
                                )
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = day,
                            color = Muted,
                            fontFamily = Poppins,
                            fontWeight = FontWeight.Normal,
                            fontSize = 8.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetsPage(
    budgets: List<BudgetEntity>,
    transactions: List<ExpenseEntity>,
    amount: String,
    onAmount: (String) -> Unit,
    category: String,
    onCategory: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Budgets", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmount,
                    label = { Text("Budget amount ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        (listOf("Overall") + categories).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onCategory(option)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Create budget") }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
            items(budgets) { budget ->
                val spent = transactions.filter {
                    it.transactionType == "EXPENSE" &&
                            (budget.category == "Overall" || it.category == budget.category)
                }.sumOf { it.amount }
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(budget.category, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { onDelete(budget.id) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete budget", tint = Muted)
                            }
                        }
                        BudgetProgress(budget.category, spent, budget.amount)
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetProgress(category: String, spent: Double, limit: Double) {
    val ratio = if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Spent ₹${money(spent)}", color = Muted, fontSize = 12.sp)
            Text("Limit ₹${money(limit)}", color = Muted, fontSize = 12.sp)
        }
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth(),
            color = if (ratio >= 1f) Coral else Purple,
            trackColor = Color(0xFF26324A)
        )
        if (ratio >= 0.8f) {
            Text(if (ratio >= 1f) "$category budget exceeded" else "$category budget reached 80%", color = Coral, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CategoriesPage(items: List<ExpenseEntity>) {
    val totals = items.filter { it.transactionType == "EXPENSE" }
        .groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.amount } }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Categories", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        items(categories) { categoryName ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(categoryName, color = Ink)
                    Text("₹${money(totals[categoryName] ?: 0.0)}", color = Muted)
                }
            }
        }
    }
}

@Composable
private fun MerchantsPage(items: List<ExpenseEntity>) {
    val merchants = items.filter { it.transactionType == "EXPENSE" }
        .groupBy { it.merchant }.mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList().sortedByDescending { it.second }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Merchant groups", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        items(merchants) { (merchant, total) ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(merchant, color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("₹${money(total)}", color = Coral)
                }
            }
        }
    }
}

@Composable
private fun RecurringPage(
    items: List<RecurringEntity>,
    name: String,
    onName: (String) -> Unit,
    amount: String,
    onAmount: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Recurring payments", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = onName, label = { Text("Subscription / payment") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmount,
                    label = { Text("Amount ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Add recurring payment") }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
            items(items) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = Ink)
                            Text("${item.frequency} · ${item.category}", color = Muted, fontSize = 12.sp)
                        }
                        Text("₹${money(item.amount)}", color = Coral)
                        IconButton(onClick = { onDelete(item.id) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmsPage(enabled: Boolean, onScan: () -> Unit, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SMS auto tracking", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Scan bank and UPI SMS from a selected date range. Processing stays on this device.", color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (enabled) "SMS permission enabled" else "SMS permission disabled", Modifier.weight(1f), color = if (enabled) Green else Coral, fontWeight = FontWeight.Bold)
                    Switch(checked = enabled, onCheckedChange = { onToggle() })
                }
                Button(onClick = onScan, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose Period & Scan SMS")
                }
                Text("Detected transactions are shown for review before they are saved. OTP, promotional, failed and reversed messages are filtered.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingsPage(sms: Boolean, onSms: () -> Unit, onCsv: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Privacy & local data", color = Ink, fontWeight = FontWeight.Bold)
                Text("Expense records are stored in the local app database.", color = Muted)
                OutlinedButton(onClick = onSms, modifier = Modifier.fillMaxWidth()) {
                    Text(if (sms) "Manage SMS tracking" else "Enable SMS tracking")
                }
                Button(onClick = onCsv, modifier = Modifier.fillMaxWidth()) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export transactions CSV")
                }
            }
        }
    }
}

/**
 * Receives new SMS messages while SMS tracking is enabled. The manifest must register
 * this receiver for android.provider.Telephony.SMS_RECEIVED and request RECEIVE_SMS.
 */
class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("sms_tracking", false)
        ) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isNullOrEmpty()) return@launch

                // Combine message parts from the same sender into a single SMS body.
                val grouped = messages.groupBy { it.originatingAddress.orEmpty() }
                for ((sender, parts) in grouped) {
                    val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
                    val timestamp = parts.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
                    val parsed = SmsParser.parse(sender, body) ?: continue

                    val db = ExpenseDatabase.get(context)
                    val expense = ExpenseEntity(
                        amount = parsed.amount,
                        merchant = parsed.merchant,
                        receiver = parsed.receiver,
                        category = parsed.category,
                        transactionType = parsed.type,
                        paymentMethod = "UPI / Bank SMS",
                        bankName = detectBankName(sender, body),
                        accountReference = extractAccountReference(body),
                        transactionReference = extractTransactionReference(body),
                        transactionDate = timestamp,
                        smsSender = parsed.sender,
                        smsHash = parsed.hash,
                        notes = "Automatically detected from SMS",
                        manuallyAdded = false
                    )

                    // Room's existing insert operation should use IGNORE conflict strategy
                    // for the smsHash/unique key to prevent duplicates.
                    val insertedId = db.expenseDao().insert(expense)
                    if (insertedId != -1L) {
                        showTransactionNotification(context, expense)
                    }
                }
            } catch (_: SecurityException) {
                // Permissions may have been revoked after tracking was enabled.
            } catch (_: Exception) {
                // Do not crash the system SMS delivery process.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private const val TRANSACTION_NOTIFICATION_CHANNEL_ID = "transaction_alerts"
private const val TRANSACTION_NOTIFICATION_CHANNEL_NAME = "Transaction Alerts"

private fun showTransactionNotification(context: Context, item: ExpenseEntity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
    ) return

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(
            TRANSACTION_NOTIFICATION_CHANNEL_ID,
            TRANSACTION_NOTIFICATION_CHANNEL_NAME,
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when bank SMS transactions are automatically detected"
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    val isCredit = item.transactionType.equals("INCOME", ignoreCase = true)
    val amountText = (if (isCredit) "+" else "−") + "₹" + money(item.amount)
    val title = if (isCredit) "CREDIT ALERT" else "DEBIT ALERT"
    val message = if (isCredit) "Money received in your account." else "Money spent from your account."
    val transactionName = item.receiver.ifBlank { item.merchant.ifBlank { item.category } }
    val dateText = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(item.transactionDate))
    val timeText = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(item.transactionDate))
    val details = "${item.paymentMethod.ifBlank { "Bank SMS" }} · $dateText"
    val amountColor = if (isCredit) android.graphics.Color.rgb(53, 217, 128) else android.graphics.Color.rgb(255, 107, 114)

    // Custom notification views provide the navy alert-card design from the reference.
    val compactView = RemoteViews(context.packageName, R.layout.notification_transaction).apply {
        setTextViewText(R.id.notification_title, title)
        setTextViewText(R.id.notification_message, message)
        setTextViewText(R.id.notification_transaction_name, transactionName)
        setTextViewText(R.id.notification_details, details)
        setTextViewText(R.id.notification_amount, amountText)
        setTextViewText(R.id.notification_time, timeText)
        setTextColor(R.id.notification_amount, amountColor)
        setTextViewText(R.id.notification_symbol, if (isCredit) "↙" else "↗")
        setTextColor(R.id.notification_symbol, amountColor)
    }

    val expandedView = RemoteViews(context.packageName, R.layout.notification_transaction_expanded).apply {
        setTextViewText(R.id.notification_title, title)
        setTextViewText(R.id.notification_message, message)
        setTextViewText(R.id.notification_transaction_name, transactionName)
        setTextViewText(R.id.notification_details, details)
        setTextViewText(R.id.notification_amount, amountText)
        setTextViewText(R.id.notification_time, timeText)
        setTextColor(R.id.notification_amount, amountColor)
        setTextViewText(R.id.notification_symbol, if (isCredit) "↙" else "↗")
        setTextColor(R.id.notification_symbol, amountColor)
    }

    val notification = NotificationCompat.Builder(context, TRANSACTION_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_more)
        .setContentTitle(title)
        .setContentText("$message $transactionName $amountText")
        .setCustomContentView(compactView)
        .setCustomBigContentView(expandedView)
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setColor(android.graphics.Color.parseColor("#0B1F3A"))
        .setColorized(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setAutoCancel(true)
        .build()

    try {
        NotificationManagerCompat.from(context).notify(item.smsHash.hashCode(), notification)
    } catch (_: SecurityException) {
        // Notification permission is optional; transaction has already been saved.
    }
}

private fun scanRecentSms(
    activity: ComponentActivity,
    existingHashes: Set<String>,
    lookbackDays: Int = 7,
    reprocessExisting: Boolean = false
): List<ExpenseEntity> {

    require(lookbackDays > 0) { "lookbackDays must be greater than zero" }

    val cutoff = System.currentTimeMillis() -
            lookbackDays.toLong() * 24L * 60L * 60L * 1000L

    val projection = arrayOf(
        Telephony.Sms.DATE,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY
    )

    val selection = "${Telephony.Sms.DATE} >= ?"
    val selectionArgs = arrayOf(cutoff.toString())

    val detected = mutableListOf<ExpenseEntity>()
    val seenHashes = mutableSetOf<String>()

    val cursor: Cursor? = activity.contentResolver.query(
        Telephony.Sms.Inbox.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        "${Telephony.Sms.DATE} DESC"
    )

    cursor?.use { c ->
        val dateIndex = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
        val senderIndex = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val bodyIndex = c.getColumnIndexOrThrow(Telephony.Sms.BODY)

        while (c.moveToNext()) {
            val date = c.getLong(dateIndex)
            val sender = c.getString(senderIndex).orEmpty()
            val body = c.getString(bodyIndex).orEmpty()

            // Re-parse each original inbox SMS with the latest SmsParser patterns.
            val parsed = SmsParser.parse(sender, body) ?: continue

            // Deduplicate messages inside this scan session.
            if (!seenHashes.add(parsed.hash)) continue

            // Skip already imported transactions unless reprocessing is requested.
            if (!reprocessExisting && parsed.hash in existingHashes) continue

            val bankName = detectBankName(sender, body)
            val accountReference = extractAccountReference(body)
            val learned = findMerchantLearningRule(
                activity = activity,
                bankName = bankName,
                accountReference = accountReference,
                smsSender = sender,
                originalDescriptor = parsed.merchant
            )

            detected += ExpenseEntity(
                amount = parsed.amount,
                merchant = learned?.first ?: parsed.merchant,
                receiver = learned?.first ?: parsed.receiver,
                category = learned?.second ?: parsed.category,
                transactionType = parsed.type,
                paymentMethod = "UPI / Bank SMS",
                bankName = bankName,
                accountReference = accountReference,
                transactionReference = extractTransactionReference(body),
                transactionDate = date,
                smsSender = parsed.sender,
                smsHash = parsed.hash,
                notes = if (learned != null) "Automatically detected from SMS · merchant rule applied" else "Automatically detected from SMS",
                manuallyAdded = false
            )
        }
    }

    return detected
}

private fun detectBankName(sender: String, body: String): String {
    val text = (sender + " " + body).uppercase(Locale.ROOT)
    return when {
        "ICICI" in text || sender.uppercase(Locale.ROOT).contains("ICIC") -> "ICICI"
        "STATE BANK" in text || " SBI " in " $text " || sender.uppercase(Locale.ROOT).contains("SBI") -> "SBI"
        "HDFC" in text || sender.uppercase(Locale.ROOT).contains("HDFC") -> "HDFC"
        "AXIS" in text || sender.uppercase(Locale.ROOT).contains("AXIS") -> "AXIS"
        "KOTAK" in text || sender.uppercase(Locale.ROOT).contains("KOTAK") -> "KOTAK"
        "CANARA" in text -> "CANARA"
        "BANK OF BARODA" in text || "BOB" in sender.uppercase(Locale.ROOT) -> "BOB"
        else -> "OTHER"
    }
}

private fun extractAccountReference(body: String): String {
    val patterns = listOf(
        Regex("""(?:a/c|acct|account)\s*(?:no\.?|number)?\s*[xX*\s-]*([0-9]{3,6})""", RegexOption.IGNORE_CASE),
        Regex("""(?:ending|xx|x{3,})\s*([0-9]{3,6})""", RegexOption.IGNORE_CASE)
    )
    return patterns.firstNotNullOfOrNull { regex -> regex.find(body)?.groupValues?.get(1) }.orEmpty()
}

private fun extractTransactionReference(body: String): String {
    val patterns = listOf(
        Regex("""(?:UPI|info)\s*[:/ ]\s*([A-Z0-9-]{8,})""", RegexOption.IGNORE_CASE),
        Regex("""(?:ref(?:erence)?(?: no)?|txn id)\s*[:# ]\s*([A-Z0-9-]{6,})""", RegexOption.IGNORE_CASE)
    )
    return patterns.firstNotNullOfOrNull { regex -> regex.find(body)?.groupValues?.get(1) }.orEmpty()
}

private fun merchantRuleKey(bankName: String, accountReference: String, smsSender: String, descriptor: String): String {
    val normalized = listOf(bankName, accountReference, smsSender, descriptor)
        .joinToString("|") { it.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), " ") }
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "merchant_learning_$digest"
}

private fun saveMerchantLearningRule(
    activity: ComponentActivity,
    bankName: String,
    accountReference: String,
    smsSender: String,
    originalDescriptor: String,
    learnedMerchant: String,
    learnedCategory: String
) {
    val exactKey = merchantRuleKey(bankName, accountReference, smsSender, originalDescriptor)
    // Also keep a sender+descriptor fallback so older transactions with missing bank/account
    // metadata can still teach a rule for later messages from the same SMS sender.
    val fallbackKey = merchantRuleKey("", "", smsSender, originalDescriptor)
    val value = "$learnedMerchant\u001F$learnedCategory"
    activity.getSharedPreferences("merchant_learning", 0).edit {
        putString(exactKey, value)
        putString(fallbackKey, value)
    }
}

private fun findMerchantLearningRule(
    activity: ComponentActivity,
    bankName: String,
    accountReference: String,
    smsSender: String,
    originalDescriptor: String
): Pair<String, String>? {
    val prefs = activity.getSharedPreferences("merchant_learning", 0)
    val exactKey = merchantRuleKey(bankName, accountReference, smsSender, originalDescriptor)
    val fallbackKey = merchantRuleKey("", "", smsSender, originalDescriptor)
    val value = prefs.getString(exactKey, null) ?: prefs.getString(fallbackKey, null) ?: return null
    val parts = value.split('\u001F', limit = 2)
    if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
    return parts[0] to parts[1]
}

private fun exportCsv(activity: ComponentActivity, items: List<ExpenseEntity>) {
    try {
        val file = File(activity.cacheDir, "transactions.csv")
        FileWriter(file).use { writer ->
            writer.append("Date,Amount,Merchant,Receiver,Category,Transaction Type,Payment Method,Bank,Notes\n")
            items.forEach { item ->
                val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(item.transactionDate))
                val fields = listOf(
                    date, item.amount.toString(), item.merchant, item.receiver,
                    item.category, item.transactionType, item.paymentMethod,
                    item.bankName, item.notes
                )
                writer.append(fields.joinToString(",") { field ->
                    "\"" + field.replace("\"", "\"\"") + "\""
                }.plus("\n"))
            }
        }
        Toast.makeText(activity, "CSV file created: ${file.name}", Toast.LENGTH_LONG).show()
    } catch (error: Exception) {
        Toast.makeText(activity, "CSV export failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
}

private fun money(value: Double): String = String.format(Locale.US, "%,.2f", value)

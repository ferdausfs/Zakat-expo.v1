package com.ritesh.cashiro.presentation.ui.features.zakat.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.database.entity.FitrEntryEntity
import com.ritesh.cashiro.data.database.entity.LivestockAnimalType
import com.ritesh.cashiro.data.database.entity.LivestockEntryEntity
import com.ritesh.cashiro.data.database.entity.UshrEntryEntity
import com.ritesh.cashiro.data.database.entity.UshrIrrigationType
import com.ritesh.cashiro.data.database.entity.ZakatLiabilityEntity
import com.ritesh.cashiro.data.database.entity.ZakatPaymentEntity
import com.ritesh.cashiro.data.database.entity.ZakatPaymentKind
import com.ritesh.cashiro.data.repository.ZakatRepository
import com.ritesh.cashiro.domain.zakat.LivestockCalculator
import com.ritesh.cashiro.domain.zakat.UshrCalculator
import com.ritesh.cashiro.domain.zakat.ZakatulFitrCalculator
import com.ritesh.cashiro.presentation.ui.components.CustomTitleTopAppBar
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared ViewModel for the five zakat module screens: debts (2.1), Ushr (5),
 * livestock (6), Zakatul Fitr (9) and the payment/sadaqah log (12).
 */
@HiltViewModel
class ZakatModulesViewModel @Inject constructor(
    private val zakatRepository: ZakatRepository
) : ViewModel() {

    val liabilities: StateFlow<List<ZakatLiabilityEntity>> =
        zakatRepository.observeLiabilities()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ushrEntries: StateFlow<List<UshrEntryEntity>> =
        zakatRepository.observeUshrEntries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val livestockEntries: StateFlow<List<LivestockEntryEntity>> =
        zakatRepository.observeLivestockEntries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fitrEntries: StateFlow<List<FitrEntryEntity>> =
        zakatRepository.observeFitrEntries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val payments: StateFlow<List<ZakatPaymentEntity>> =
        zakatRepository.observePayments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveLiability(entry: ZakatLiabilityEntity) =
        viewModelScope.launch { zakatRepository.upsertLiability(entry) }

    fun deleteLiability(id: Long) =
        viewModelScope.launch { zakatRepository.deleteLiability(id) }

    fun saveUshr(entry: UshrEntryEntity) =
        viewModelScope.launch { zakatRepository.upsertUshrEntry(entry) }

    fun deleteUshr(id: Long) =
        viewModelScope.launch { zakatRepository.deleteUshrEntry(id) }

    fun saveLivestock(entry: LivestockEntryEntity) =
        viewModelScope.launch { zakatRepository.upsertLivestockEntry(entry) }

    fun deleteLivestock(id: Long) =
        viewModelScope.launch { zakatRepository.deleteLivestockEntry(id) }

    fun saveFitr(entry: FitrEntryEntity) =
        viewModelScope.launch { zakatRepository.upsertFitrEntry(entry) }

    fun deleteFitr(id: Long) =
        viewModelScope.launch { zakatRepository.deleteFitrEntry(id) }

    fun savePayment(entry: ZakatPaymentEntity) =
        viewModelScope.launch { zakatRepository.upsertPayment(entry) }

    fun deletePayment(id: Long) =
        viewModelScope.launch { zakatRepository.deletePayment(id) }
}

// =====================================================================
// Shared small pieces
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModuleScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    fabContentDescription: String,
    onFabClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = title,
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                containerColor = MaterialTheme.colorScheme.primary,
                onClick = onFabClick
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = fabContentDescription
                )
            }
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}

@Composable
internal fun ModuleEmptyHint(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun amountFilter(input: String, onChange: (String) -> Unit) {
    if (input.matches(Regex("^\\d*[.,]?\\d*$"))) onChange(input)
}

// =====================================================================
// Debts / liabilities (spec 2.1)
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZakatLiabilitiesScreen(
    onNavigateBack: () -> Unit,
    viewModel: ZakatModulesViewModel = hiltViewModel()
) {
    val currencyCode = com.ritesh.cashiro.data.model.Currency.DEFAULT_CURRENCY_CODE
    val entries by viewModel.liabilities.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    ModuleScaffold(
        title = stringResource(R.string.zakat_modules_liabilities),
        onNavigateBack = onNavigateBack,
        fabContentDescription = stringResource(R.string.zakat_liability_add),
        onFabClick = { showEditor = true }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Spacing.md, end = Spacing.md,
                top = paddingValues.calculateTopPadding() + Spacing.md,
                bottom = Spacing.xxl + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = stringResource(R.string.zakat_deductions_line),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrency(
                                com.ritesh.cashiro.domain.zakat.WealthPoolCalculator
                                    .nearTermDebts(entries, today),
                                currencyCode
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.zakat_liability_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entries.isEmpty()) {
                item { ModuleEmptyHint(stringResource(R.string.zakat_liability_empty)) }
            }
            items(entries, key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrency(entry.amount, currencyCode) +
                                " • " + entry.dueDate.toString(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.deleteLiability(entry.id) }) {
                                Text(stringResource(R.string.zakat_asset_delete))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            sheetState = sheetState
        ) {
            var name by remember { mutableStateOf("") }
            var amount by remember { mutableStateOf("") }
            var dueDate by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xxl)
            ) {
                Text(
                    text = stringResource(R.string.zakat_liability_add),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.zakat_liability_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amountFilter(it) { amount = it } },
                    label = { Text(stringResource(R.string.zakat_liability_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.zakat_liability_due_date) +
                            ": " + dueDate,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row {
                        Button(
                            onClick = { dueDate = dueDate.minusMonths(1) },
                            enabled = dueDate > today
                        ) { Text("-1m") }
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Button(onClick = { dueDate = dueDate.plusMonths(1) }) {
                            Text("+1m")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)
                ) {
                    TextButton(onClick = { showEditor = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Button(
                        onClick = {
                            val value = amount.toBigDecimalOrNull()
                                ?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
                            viewModel.saveLiability(
                                ZakatLiabilityEntity(
                                    name = name.trim(),
                                    amount = value,
                                    dueDate = dueDate
                                )
                            )
                            showEditor = false
                        },
                        enabled = name.isNotBlank() && amount.toBigDecimalOrNull() != null
                    ) {
                        Text(stringResource(R.string.zakat_asset_save))
                    }
                }
            }
        }
    }
}

// =====================================================================
// Ushr (spec 5)
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UshrScreen(
    onNavigateBack: () -> Unit,
    viewModel: ZakatModulesViewModel = hiltViewModel()
) {
    val currencyCode = com.ritesh.cashiro.data.model.Currency.DEFAULT_CURRENCY_CODE
    val entries by viewModel.ushrEntries.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }

    ModuleScaffold(
        title = stringResource(R.string.ushr_title),
        onNavigateBack = onNavigateBack,
        fabContentDescription = stringResource(R.string.ushr_add),
        onFabClick = { showEditor = true }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Spacing.md, end = Spacing.md,
                top = paddingValues.calculateTopPadding() + Spacing.md,
                bottom = Spacing.xxl + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (entries.isEmpty()) {
                item { ModuleEmptyHint(stringResource(R.string.ushr_empty)) }
            }
            items(entries, key = { it.id }) { entry ->
                val result = remember(entry) { UshrCalculator.calculate(entry) }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.cropName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (entry.isPaid) {
                                Text(
                                    text = stringResource(R.string.ushr_paid),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "${entry.quantityKg.stripTrailingZeros().toPlainString()} kg • " +
                                CurrencyFormatter.formatCurrency(entry.marketValue, currencyCode) +
                                " • " + entry.harvestDate,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (result.thresholdMet) {
                                stringResource(
                                    R.string.ushr_due_format,
                                    CurrencyFormatter.formatCurrency(result.ushrDue, currencyCode)
                                )
                            } else {
                                stringResource(R.string.ushr_below_threshold)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (result.thresholdMet) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (!entry.isPaid && result.thresholdMet) {
                                TextButton(onClick = {
                                    viewModel.saveUshr(entry.copy(isPaid = true))
                                }) {
                                    Text(stringResource(R.string.fitr_mark_paid))
                                }
                            }
                            TextButton(onClick = { viewModel.deleteUshr(entry.id) }) {
                                Text(stringResource(R.string.zakat_asset_delete))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            sheetState = sheetState
        ) {
            var crop by remember { mutableStateOf("") }
            var qty by remember { mutableStateOf("") }
            var value by remember { mutableStateOf("") }
            var irrigation by remember {
                mutableStateOf(UshrIrrigationType.NATURAL.name)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xxl)
            ) {
                Text(
                    text = stringResource(R.string.ushr_add),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = crop,
                    onValueChange = { crop = it },
                    label = { Text(stringResource(R.string.ushr_crop)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = qty,
                    onValueChange = { amountFilter(it) { qty = it } },
                    label = { Text(stringResource(R.string.ushr_quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = value,
                    onValueChange = { amountFilter(it) { value = it } },
                    label = { Text(stringResource(R.string.ushr_value)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.ushr_irrigation),
                    style = MaterialTheme.typography.labelLarge
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(UshrIrrigationType.entries) { t ->
                        FilterChip(
                            selected = irrigation == t.name,
                            onClick = { irrigation = t.name },
                            label = {
                                Text(
                                    stringResource(
                                        when (t) {
                                            UshrIrrigationType.NATURAL -> R.string.ushr_irrigation_natural
                                            UshrIrrigationType.ARTIFICIAL -> R.string.ushr_irrigation_artificial
                                            UshrIrrigationType.MIXED -> R.string.ushr_irrigation_mixed
                                        }
                                    )
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)
                ) {
                    TextButton(onClick = { showEditor = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Button(
                        onClick = {
                            viewModel.saveUshr(
                                UshrEntryEntity(
                                    cropName = crop.trim(),
                                    quantityKg = qty.toBigDecimalOrNull()
                                        ?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO,
                                    marketValue = value.toBigDecimalOrNull()
                                        ?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO,
                                    irrigationType = irrigation
                                )
                            )
                            showEditor = false
                        },
                        enabled = crop.isNotBlank() &&
                            qty.toBigDecimalOrNull() != null &&
                            value.toBigDecimalOrNull() != null
                    ) {
                        Text(stringResource(R.string.zakat_asset_save))
                    }
                }
            }
        }
    }
}

// =====================================================================
// Livestock (spec 6)
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivestockScreen(
    onNavigateBack: () -> Unit,
    viewModel: ZakatModulesViewModel = hiltViewModel()
) {
    val entries by viewModel.livestockEntries.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }

    ModuleScaffold(
        title = stringResource(R.string.livestock_title),
        onNavigateBack = onNavigateBack,
        fabContentDescription = stringResource(R.string.livestock_add),
        onFabClick = { showEditor = true }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Spacing.md, end = Spacing.md,
                top = paddingValues.calculateTopPadding() + Spacing.md,
                bottom = Spacing.xxl + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Text(
                    text = stringResource(R.string.livestock_commercial_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entries.isEmpty()) {
                item { ModuleEmptyHint(stringResource(R.string.livestock_empty)) }
            }
            items(entries, key = { it.id }) { entry ->
                val due = remember(entry) { LivestockCalculator.calculate(entry) }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(
                                when (
                                    runCatching {
                                        LivestockAnimalType.valueOf(entry.animalType)
                                    }.getOrDefault(LivestockAnimalType.SHEEP)
                                ) {
                                    LivestockAnimalType.CAMEL -> R.string.livestock_type_camel
                                    LivestockAnimalType.CATTLE -> R.string.livestock_type_cattle
                                    LivestockAnimalType.SHEEP -> R.string.livestock_type_sheep
                                }
                            ) + " • ${entry.count}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (due.nisabMet) {
                                stringResource(
                                    R.string.livestock_due_format,
                                    due.description
                                )
                            } else {
                                stringResource(R.string.livestock_below_nisab)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (due.nisabMet) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.deleteLivestock(entry.id) }) {
                                Text(stringResource(R.string.zakat_asset_delete))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            sheetState = sheetState
        ) {
            var name by remember { mutableStateOf("") }
            var animalType by remember { mutableStateOf(LivestockAnimalType.SHEEP.name) }
            var count by remember { mutableStateOf("") }
            var grazing by remember { mutableStateOf(true) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xxl)
            ) {
                Text(
                    text = stringResource(R.string.livestock_add),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.livestock_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.livestock_type),
                    style = MaterialTheme.typography.labelLarge
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(LivestockAnimalType.entries) { t ->
                        FilterChip(
                            selected = animalType == t.name,
                            onClick = { animalType = t.name },
                            label = {
                                Text(
                                    stringResource(
                                        when (t) {
                                            LivestockAnimalType.CAMEL -> R.string.livestock_type_camel
                                            LivestockAnimalType.CATTLE -> R.string.livestock_type_cattle
                                            LivestockAnimalType.SHEEP -> R.string.livestock_type_sheep
                                        }
                                    )
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = count,
                    onValueChange = { input ->
                        if (input.matches(Regex("^\\d*$"))) count = input
                    },
                    label = { Text(stringResource(R.string.livestock_count)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.livestock_grazing),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = grazing, onCheckedChange = { grazing = it })
                }
                Spacer(modifier = Modifier.height(Spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)
                ) {
                    TextButton(onClick = { showEditor = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Button(
                        onClick = {
                            viewModel.saveLivestock(
                                LivestockEntryEntity(
                                    name = name.trim(),
                                    animalType = animalType,
                                    count = count.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                                    isGrazing = grazing
                                )
                            )
                            showEditor = false
                        },
                        enabled = name.isNotBlank() &&
                            (count.toIntOrNull() ?: 0) > 0
                    ) {
                        Text(stringResource(R.string.zakat_asset_save))
                    }
                }
            }
        }
    }
}

// =====================================================================
// Zakatul Fitr (spec 9)
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitrScreen(
    onNavigateBack: () -> Unit,
    viewModel: ZakatModulesViewModel = hiltViewModel()
) {
    val currencyCode = com.ritesh.cashiro.data.model.Currency.DEFAULT_CURRENCY_CODE
    val entries by viewModel.fitrEntries.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }

    ModuleScaffold(
        title = stringResource(R.string.fitr_title),
        onNavigateBack = onNavigateBack,
        fabContentDescription = stringResource(R.string.fitr_add),
        onFabClick = { showEditor = true }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Spacing.md, end = Spacing.md,
                top = paddingValues.calculateTopPadding() + Spacing.md,
                bottom = Spacing.xxl + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            if (entries.isEmpty()) {
                item { ModuleEmptyHint(stringResource(R.string.fitr_empty)) }
            }
            items(entries, key = { it.id }) { entry ->
                val result = remember(entry) { ZakatulFitrCalculator.calculate(entry) }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.yearLabel.ifBlank { entry.stapleName },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (entry.isPaid) {
                                Text(
                                    text = stringResource(
                                        R.string.fitr_paid_on,
                                        entry.paidAt?.toString() ?: ""
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = entry.stapleName +
                                " • " + entry.kgPerPerson.stripTrailingZeros()
                                .toPlainString() + " kg × " + entry.householdCount,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(
                                R.string.fitr_per_person_format,
                                CurrencyFormatter.formatCurrency(
                                    result.amountPerPerson, currencyCode
                                )
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(
                                R.string.fitr_due_format,
                                CurrencyFormatter.formatCurrency(result.totalDue, currencyCode)
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (!entry.isPaid) {
                                TextButton(onClick = {
                                    viewModel.saveFitr(
                                        entry.copy(isPaid = true, paidAt = LocalDate.now())
                                    )
                                }) {
                                    Text(stringResource(R.string.fitr_mark_paid))
                                }
                            }
                            TextButton(onClick = { viewModel.deleteFitr(entry.id) }) {
                                Text(stringResource(R.string.zakat_asset_delete))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            sheetState = sheetState
        ) {
            var yearLabel by remember { mutableStateOf("") }
            var staple by remember { mutableStateOf("") }
            var price by remember { mutableStateOf("") }
            var kgPerPerson by remember { mutableStateOf("2.5") }
            var household by remember { mutableStateOf("1") }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xxl)
            ) {
                Text(
                    text = stringResource(R.string.fitr_add),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = yearLabel,
                    onValueChange = { yearLabel = it },
                    label = { Text(stringResource(R.string.fitr_year)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = staple,
                    onValueChange = { staple = it },
                    label = { Text(stringResource(R.string.fitr_staple)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = price,
                    onValueChange = { amountFilter(it) { price = it } },
                    label = { Text(stringResource(R.string.fitr_price_per_kg)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = kgPerPerson,
                    onValueChange = { amountFilter(it) { kgPerPerson = it } },
                    label = { Text(stringResource(R.string.fitr_kg_per_person)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = household,
                    onValueChange = { input ->
                        if (input.matches(Regex("^\\d*$"))) household = input
                    },
                    label = { Text(stringResource(R.string.fitr_household)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)
                ) {
                    TextButton(onClick = { showEditor = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Button(
                        onClick = {
                            viewModel.saveFitr(
                                FitrEntryEntity(
                                    yearLabel = yearLabel.trim(),
                                    stapleName = staple.trim(),
                                    pricePerKg = price.toBigDecimalOrNull()
                                        ?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO,
                                    kgPerPerson = kgPerPerson.toBigDecimalOrNull()
                                        ?.max(BigDecimal("0.1"))
                                        ?: ZakatulFitrCalculator.DEFAULT_KG_PER_PERSON,
                                    householdCount = household.toIntOrNull()
                                        ?.coerceAtLeast(1) ?: 1
                                )
                            )
                            showEditor = false
                        },
                        enabled = price.toBigDecimalOrNull() != null &&
                            (household.toIntOrNull() ?: 0) > 0
                    ) {
                        Text(stringResource(R.string.zakat_asset_save))
                    }
                }
            }
        }
    }
}

// =====================================================================
// Payments & sadaqah log (spec 12)
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZakatPaymentsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ZakatModulesViewModel = hiltViewModel()
) {
    val currencyCode = com.ritesh.cashiro.data.model.Currency.DEFAULT_CURRENCY_CODE
    val entries by viewModel.payments.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }

    ModuleScaffold(
        title = stringResource(R.string.payments_title),
        onNavigateBack = onNavigateBack,
        fabContentDescription = stringResource(R.string.payments_add),
        onFabClick = { showEditor = true }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Spacing.md, end = Spacing.md,
                top = paddingValues.calculateTopPadding() + Spacing.md,
                bottom = Spacing.xxl + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = stringResource(R.string.payments_total_format, ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrency(
                                entries.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) },
                                currencyCode
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            if (entries.isEmpty()) {
                item { ModuleEmptyHint(stringResource(R.string.payments_empty)) }
            }
            items(entries, key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = stringResource(
                                if (entry.kind == ZakatPaymentKind.SADAQAH.name) {
                                    R.string.payments_kind_sadaqah
                                } else {
                                    R.string.payments_kind_zakat
                                }
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrency(entry.amount, currencyCode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val details = buildString {
                            append(entry.date)
                            if (entry.recipient.isNotBlank()) append(" • ").append(entry.recipient)
                            if (entry.category.isNotBlank()) append(" • ").append(entry.category)
                        }
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.deletePayment(entry.id) }) {
                                Text(stringResource(R.string.zakat_asset_delete))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            sheetState = sheetState
        ) {
            var kind by remember { mutableStateOf(ZakatPaymentKind.ZAKAT.name) }
            var amount by remember { mutableStateOf("") }
            var recipient by remember { mutableStateOf("") }
            var category by remember { mutableStateOf("") }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xxl)
            ) {
                Text(
                    text = stringResource(R.string.payments_add),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.payments_kind),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    FilterChip(
                        selected = kind == ZakatPaymentKind.ZAKAT.name,
                        onClick = { kind = ZakatPaymentKind.ZAKAT.name },
                        label = { Text(stringResource(R.string.payments_kind_zakat)) }
                    )
                    FilterChip(
                        selected = kind == ZakatPaymentKind.SADAQAH.name,
                        onClick = { kind = ZakatPaymentKind.SADAQAH.name },
                        label = { Text(stringResource(R.string.payments_kind_sadaqah)) }
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amountFilter(it) { amount = it } },
                    label = { Text(stringResource(R.string.payments_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text(stringResource(R.string.payments_recipient)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.payments_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)
                ) {
                    TextButton(onClick = { showEditor = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Button(
                        onClick = {
                            viewModel.savePayment(
                                ZakatPaymentEntity(
                                    kind = kind,
                                    amount = amount.toBigDecimalOrNull()
                                        ?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO,
                                    recipient = recipient.trim(),
                                    category = category.trim()
                                )
                            )
                            showEditor = false
                        },
                        enabled = amount.toBigDecimalOrNull() != null &&
                            (amount.toBigDecimalOrNull()?.signum() ?: 0) > 0
                    ) {
                        Text(stringResource(R.string.zakat_asset_save))
                    }
                }
            }
        }
    }
}

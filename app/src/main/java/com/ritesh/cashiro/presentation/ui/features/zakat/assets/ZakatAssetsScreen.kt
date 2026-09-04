package com.ritesh.cashiro.presentation.ui.features.zakat.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.database.entity.ZakatAssetEntity
import com.ritesh.cashiro.data.database.entity.ZakatAssetType
import com.ritesh.cashiro.data.database.entity.ZakatAssetUnit
import com.ritesh.cashiro.domain.zakat.WealthPoolCalculator
import com.ritesh.cashiro.presentation.ui.components.CustomTitleTopAppBar
import com.ritesh.cashiro.presentation.ui.components.CashiroCard
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.TextStyle
import com.ritesh.cashiro.presentation.ui.features.zakat.ZakatSectionUi
import java.time.format.FormatStyle

/**
 * Zakat assets ledger (Phase 2b).
 *
 * A running list of every tracked asset entry — metals valued live at
 * the user-maintained per-gram rate, non-metals at their entered value —
 * with add, edit and delete. The pattern mirrors the existing
 * Transactions list: a top bar, a summary card, a card-per-entry list
 * and a bottom sheet for data entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZakatAssetsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ZakatAssetsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var editingAsset by remember { mutableStateOf<ZakatAssetEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.zakat_assets_title),
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
            androidx.compose.material3.FloatingActionButton(
                containerColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    editingAsset = null
                    showEditor = true
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.zakat_asset_add)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Spacing.md,
                end = Spacing.md,
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
                            text = stringResource(R.string.zakat_assets_total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrency(
                                state.totalValue, state.currencyCode
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (state.rows.isEmpty() && !state.loading) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.zakat_asset_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = stringResource(R.string.zakat_asset_empty_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(state.rows, key = { it.asset.id }) { row ->
                AssetCard(
                    row = row,
                    currencyCode = state.currencyCode,
                    onClick = {
                        editingAsset = row.asset
                        showEditor = true
                    },
                    onDelete = { viewModel.deleteAsset(row.asset.id) }
                )
            }
        }
    }

    if (showEditor) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            sheetState = sheetState
        ) {
            AssetEditorSheet(
                initial = editingAsset,
                defaultCurrency = state.currencyCode,
                goldPricePerGram = state.goldPricePerGram,
                silverPricePerGram = state.silverPricePerGram,
                onSave = { asset ->
                    viewModel.saveAsset(asset)
                    showEditor = false
                },
                onCancel = { showEditor = false }
            )
        }
    }
}

@Composable
private fun AssetCard(
    row: ZakatAssetsViewModel.AssetRow,
    currencyCode: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val asset = row.asset
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }
    // Shared card language (theme shape + surfaceContainerLow) for cohesion
    CashiroCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = asset.name.ifBlank { assetTypeLabel(asset.type) },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = assetTypeLabel(asset.type) + quantityLabel(asset),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyFormatter.formatCurrency(row.value, currencyCode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = asset.acquisitionDate.format(dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            asset.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.zakat_asset_delete))
            }
        }
    }
}

@Composable
private fun quantityLabel(asset: ZakatAssetEntity): String {
    val type = remember(asset.type) {
        runCatching { ZakatAssetType.valueOf(asset.type) }.getOrDefault(ZakatAssetType.OTHER)
    }
    if (!type.isMetal) return ""
    val unit = remember(asset.unit) {
        runCatching { ZakatAssetUnit.valueOf(asset.unit) }.getOrDefault(ZakatAssetUnit.GRAM)
    }
    val unitLabel = when (unit) {
        ZakatAssetUnit.GRAM -> stringResource(R.string.zakat_unit_gram)
        ZakatAssetUnit.VORI -> stringResource(R.string.zakat_unit_vori)
        ZakatAssetUnit.ANA -> stringResource(R.string.zakat_unit_ana)
        ZakatAssetUnit.RATTI -> stringResource(R.string.zakat_unit_ratti)
        ZakatAssetUnit.VALUE -> ""
    }
    val karatLabel = if (type == ZakatAssetType.GOLD && asset.karat != null) {
        " • " + stringResource(R.string.zakat_karat_label, asset.karat)
    } else ""
    return " • ${asset.quantity.stripTrailingZeros().toPlainString()} $unitLabel$karatLabel"
}

@Composable
private fun assetTypeLabel(type: String): String = when (
    runCatching { ZakatAssetType.valueOf(type) }.getOrDefault(ZakatAssetType.OTHER)
) {
    ZakatAssetType.GOLD -> stringResource(R.string.zakat_asset_type_gold)
    ZakatAssetType.SILVER -> stringResource(R.string.zakat_asset_type_silver)
    ZakatAssetType.PROPERTY -> stringResource(R.string.zakat_asset_type_property)
    ZakatAssetType.BUSINESS -> stringResource(R.string.zakat_asset_type_business)
    ZakatAssetType.INVESTMENT -> stringResource(R.string.zakat_asset_type_investment)
    ZakatAssetType.OTHER -> stringResource(R.string.zakat_asset_type_other)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetEditorSheet(
    initial: ZakatAssetEntity?,
    defaultCurrency: String,
    goldPricePerGram: String,
    silverPricePerGram: String,
    onSave: (ZakatAssetEntity) -> Unit,
    onCancel: () -> Unit
) {
    val isGold = initial?.type == ZakatAssetType.GOLD.name
    var type by remember { mutableStateOf(initial?.type ?: ZakatAssetType.GOLD.name) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var quantity by remember {
        mutableStateOf(
            initial?.quantity?.stripTrailingZeros()?.toPlainString() ?: ""
        )
    }
    var unit by remember {
        mutableStateOf(
            initial?.unit ?: ZakatAssetUnit.GRAM.name
        )
    }
    var karat by remember { mutableStateOf(initial?.karat ?: 22) }
    var estimatedValue by remember {
        mutableStateOf(
            initial?.estimatedValue?.stripTrailingZeros()?.toPlainString() ?: ""
        )
    }
    var currency by remember { mutableStateOf(initial?.currency ?: defaultCurrency) }
    var acquisitionDate by remember {
        mutableStateOf(initial?.acquisitionDate ?: LocalDate.now())
    }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    val assetType = runCatching { ZakatAssetType.valueOf(type) }
        .getOrDefault(ZakatAssetType.OTHER)
    val isMetal = assetType.isMetal
    val assetUnit = runCatching { ZakatAssetUnit.valueOf(unit) }
        .getOrDefault(ZakatAssetUnit.GRAM)

    // Live value preview: metals use quantity x unit x purity x price;
    // non-metals use the entered value directly.
    val previewValue = remember(type, quantity, unit, karat, estimatedValue, goldPricePerGram, silverPricePerGram) {
        if (isMetal) {
            val qty = quantity.toBigDecimalOrNull()?.max(BigDecimal.ZERO) ?: BigDecimal.ZERO
            val grams = WealthPoolCalculator.toGrams(qty, assetUnit)
            val price = when (assetType) {
                ZakatAssetType.GOLD ->
                    (goldPricePerGram.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                else -> (silverPricePerGram.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            }
            val purity = if (assetType == ZakatAssetType.GOLD) {
                WealthPoolCalculator.karatPurity(karat)
            } else BigDecimal.ONE
            grams.multiply(purity).multiply(price)
                .setScale(2, java.math.RoundingMode.HALF_UP)
        } else {
            estimatedValue.toBigDecimalOrNull()?.setScale(
                2, java.math.RoundingMode.HALF_UP
            ) ?: BigDecimal.ZERO
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xxl)
    ) {
        Text(
            text = stringResource(
                if (initial == null) R.string.zakat_asset_add else R.string.zakat_asset_edit
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        // Asset type chips
        Text(
            text = stringResource(R.string.zakat_asset_type),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            items(ZakatAssetType.entries) { t ->
                FilterChip(
                    selected = type == t.name,
                    onClick = {
                        type = t.name
                        if (!t.isMetal) unit = ZakatAssetUnit.VALUE.name
                        else if (unit == ZakatAssetUnit.VALUE.name) unit = ZakatAssetUnit.GRAM.name
                    },
                    label = { Text(assetTypeLabel(t.name)) }
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.md))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.zakat_asset_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Spacing.sm))

        if (isMetal) {
            OutlinedTextField(
                value = quantity,
                onValueChange = { input ->
                    if (input.matches(Regex("^\\d*[.,]?\\d*$"))) quantity = input
                },
                label = { Text(stringResource(R.string.zakat_asset_quantity)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.zakat_asset_unit),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                val units = if (assetType == ZakatAssetType.SILVER) {
                    ZakatAssetUnit.entries.filter { it != ZakatAssetUnit.VALUE }
                } else {
                    ZakatAssetUnit.entries.filter { it != ZakatAssetUnit.VALUE }
                }
                items(units) { u ->
                    FilterChip(
                        selected = unit == u.name,
                        onClick = { unit = u.name },
                        label = { Text(unitLabel(u)) }
                    )
                }
            }

            if (assetType == ZakatAssetType.GOLD) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.zakat_asset_karat),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(listOf(24, 22, 21, 18)) { k ->
                        FilterChip(
                            selected = karat == k,
                            onClick = { karat = k },
                            label = { Text(stringResource(R.string.zakat_karat_label, k)) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(
                    R.string.zakat_asset_value_preview,
                    CurrencyFormatter.formatCurrency(previewValue, currency)
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            OutlinedTextField(
                value = estimatedValue,
                onValueChange = { input ->
                    if (input.matches(Regex("^\\d*[.,]?\\d*$"))) estimatedValue = input
                },
                label = {
                    Text(
                        stringResource(
                            if (assetType == ZakatAssetType.BUSINESS) {
                                R.string.zakat_asset_business_value
                            } else {
                                R.string.zakat_asset_estimated_value
                            }
                        )
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            if (assetType == ZakatAssetType.BUSINESS) {
                Text(
                    text = stringResource(R.string.zakat_asset_business_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))

        OutlinedTextField(
            value = currency,
            onValueChange = { currency = it.uppercase().take(3) },
            label = { Text(stringResource(R.string.zakat_asset_currency)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.zakat_asset_acquisition_date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = acquisitionDate.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Button(onClick = { showDatePicker = true }) {
                Text(stringResource(R.string.zakat_select_date))
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.zakat_asset_notes)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1
        )

        Spacer(modifier = Modifier.height(Spacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(android.R.string.cancel))
            }
            Button(
                onClick = {
                    val qty = quantity.toBigDecimalOrNull()?.max(BigDecimal.ZERO)
                        ?: BigDecimal.ONE
                    val value = estimatedValue.toBigDecimalOrNull()
                    val entity = (initial ?: ZakatAssetEntity()).copy(
                        type = type,
                        name = name.trim(),
                        quantity = qty,
                        unit = if (isMetal) unit else ZakatAssetUnit.VALUE.name,
                        karat = if (assetType == ZakatAssetType.GOLD) karat else null,
                        currency = currency.trim().uppercase().ifBlank { defaultCurrency },
                        acquisitionDate = acquisitionDate,
                        estimatedValue = if (isMetal) value else value,
                        notes = notes.trim().takeIf { it.isNotEmpty() }
                    )
                    onSave(entity)
                },
                enabled = name.isNotBlank() &&
                    (isMetal || (estimatedValue.toBigDecimalOrNull() != null))
            ) {
                Text(stringResource(R.string.zakat_asset_save))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = acquisitionDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            acquisitionDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun unitLabel(unit: ZakatAssetUnit): String = when (unit) {
    ZakatAssetUnit.GRAM -> stringResource(R.string.zakat_unit_gram)
    ZakatAssetUnit.VORI -> stringResource(R.string.zakat_unit_vori)
    ZakatAssetUnit.ANA -> stringResource(R.string.zakat_unit_ana)
    ZakatAssetUnit.RATTI -> stringResource(R.string.zakat_unit_ratti)
    ZakatAssetUnit.VALUE -> ""
}

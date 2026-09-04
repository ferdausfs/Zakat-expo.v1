package com.ritesh.cashiro.presentation.ui.features.zakat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritesh.cashiro.R
import com.ritesh.cashiro.domain.zakat.ZakatCalculator
import com.ritesh.cashiro.presentation.ui.components.CustomTitleTopAppBar
import com.ritesh.cashiro.presentation.ui.components.CashiroCard
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.CurrencyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Zakat calculator screen (Phase 2a).
 *
 * Lets the user enter zakatable wealth (cash, gold, silver, investments,
 * debts), current gold/silver gram prices and the hawl start date, then
 * shows the nisab threshold, eligibility and the 2.5% zakat due — all in
 * the user's base currency unit (e.g. SAR or BDT).
 *
 * Since Phase 2b this screen is reached from the Zakat dashboard via the
 * [onNavigateBack] callback; the method and metal-price fields stay in
 * sync with the dashboard through the persisted zakat settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZakatScreen(
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ZakatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currencyCode = state.currencyCode

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.zakat_title),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hasBackButton = onNavigateBack != null,
                navigationContent = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null
                            )
                        }
                    }
                }
            )
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
                Column {
                    Text(
                        text = stringResource(R.string.zakat_tagline),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(R.string.zakat_currency_note, currencyCode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                HawlCard(
                    state = state,
                    onStartDateChange = viewModel::onHawlStartDateChange
                )
            }

            item {
                WealthCard(
                    state = state,
                    onCashChange = viewModel::onCashChange,
                    onGoldGramsChange = viewModel::onGoldGramsChange,
                    onSilverGramsChange = viewModel::onSilverGramsChange,
                    onInvestmentsChange = viewModel::onInvestmentsChange,
                    onDebtsOwedChange = viewModel::onDebtsOwedChange
                )
            }

            item {
                PricesCard(
                    state = state,
                    onGoldPriceChange = viewModel::onGoldPriceChange,
                    onSilverPriceChange = viewModel::onSilverPriceChange,
                    onNisabMethodChange = viewModel::onNisabMethodChange
                )
            }

            item {
                ResultCard(state = state)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HawlCard(
    state: ZakatViewModel.UiState,
    onStartDateChange: (LocalDate) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }
    val assessment = state.assessment

    SectionCard {
        Text(
            text = stringResource(R.string.zakat_hawl_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.zakat_hawl_start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.hawlStartDate.format(dateFormatter),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Button(onClick = { showDatePicker = true }) {
                Text(text = stringResource(R.string.zakat_select_date))
            }
        }

        assessment?.let { result ->
            Spacer(modifier = Modifier.height(Spacing.md))
            val fraction = if (result.hawlDaysInYear > 0) {
                (result.hawlDaysElapsed.toFloat() / result.hawlDaysInYear).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(
                    R.string.zakat_hawl_progress,
                    result.hawlDaysElapsed,
                    result.hawlDaysInYear
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = if (result.hawlComplete) {
                    stringResource(R.string.zakat_hawl_complete)
                } else {
                    stringResource(R.string.zakat_hawl_in_progress)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (result.hawlComplete) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.hawlStartDate
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
                            val picked = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onStartDateChange(picked)
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
private fun WealthCard(
    state: ZakatViewModel.UiState,
    onCashChange: (String) -> Unit,
    onGoldGramsChange: (String) -> Unit,
    onSilverGramsChange: (String) -> Unit,
    onInvestmentsChange: (String) -> Unit,
    onDebtsOwedChange: (String) -> Unit
) {
    SectionCard {
        Text(
            text = stringResource(R.string.zakat_wealth_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AmountField(
                label = stringResource(R.string.zakat_cash),
                value = state.cash,
                onValueChange = onCashChange
            )
            AmountField(
                label = stringResource(R.string.zakat_gold_grams),
                value = state.goldGrams,
                onValueChange = onGoldGramsChange
            )
            AmountField(
                label = stringResource(R.string.zakat_silver_grams),
                value = state.silverGrams,
                onValueChange = onSilverGramsChange
            )
            AmountField(
                label = stringResource(R.string.zakat_investments),
                value = state.investments,
                onValueChange = onInvestmentsChange
            )
            AmountField(
                label = stringResource(R.string.zakat_debts),
                value = state.debtsOwed,
                onValueChange = onDebtsOwedChange
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PricesCard(
    state: ZakatViewModel.UiState,
    onGoldPriceChange: (String) -> Unit,
    onSilverPriceChange: (String) -> Unit,
    onNisabMethodChange: (ZakatCalculator.NisabMethod) -> Unit
) {
    SectionCard {
        Text(
            text = stringResource(R.string.zakat_prices_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AmountField(
                label = stringResource(R.string.zakat_gold_price),
                value = state.goldPricePerGram,
                onValueChange = onGoldPriceChange
            )
            AmountField(
                label = stringResource(R.string.zakat_silver_price),
                value = state.silverPricePerGram,
                onValueChange = onSilverPriceChange
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.zakat_nisab_section),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(
                selected = state.nisabMethod == ZakatCalculator.NisabMethod.GOLD,
                onClick = { onNisabMethodChange(ZakatCalculator.NisabMethod.GOLD) },
                label = { Text(stringResource(R.string.zakat_nisab_gold)) }
            )
            FilterChip(
                selected = state.nisabMethod == ZakatCalculator.NisabMethod.SILVER,
                onClick = { onNisabMethodChange(ZakatCalculator.NisabMethod.SILVER) },
                label = { Text(stringResource(R.string.zakat_nisab_silver)) }
            )
        }

        state.assessment?.let { result ->
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(
                    R.string.zakat_gold_nisab_value,
                    CurrencyFormatter.formatCurrency(result.goldNisabValue, state.currencyCode)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.zakat_silver_nisab_value,
                    CurrencyFormatter.formatCurrency(result.silverNisabValue, state.currencyCode)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultCard(state: ZakatViewModel.UiState) {
    val result = state.assessment ?: return
    val currencyCode = state.currencyCode

    SectionCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = stringResource(R.string.zakat_result_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(Spacing.sm))

        ResultRow(
            label = stringResource(R.string.zakat_nisab_value),
            value = CurrencyFormatter.formatCurrency(result.appliedNisabValue, currencyCode),
            onContainer = true
        )
        ResultRow(
            label = stringResource(R.string.zakat_net_wealth),
            value = CurrencyFormatter.formatCurrency(result.netWealth, currencyCode),
            onContainer = true
        )

        Spacer(modifier = Modifier.height(Spacing.md))
        when {
            result.eligible -> {
                Text(
                    text = stringResource(R.string.zakat_due),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = CurrencyFormatter.formatCurrency(result.zakatDue, currencyCode),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            result.netWealth < result.appliedNisabValue -> {
                Text(
                    text = stringResource(R.string.zakat_below_nisab),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.zakat_hawl_in_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    onContainer: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onContainer) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (onContainer) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun SectionCard(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    // Uses the shared CashiroCard language (theme shape + surfaceContainerLow)
    // so the Zakat calculator matches the rest of the app.
    CashiroCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun AmountField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.matches(Regex("^\\d*[.,]?\\d*$"))) {
                onValueChange(input)
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

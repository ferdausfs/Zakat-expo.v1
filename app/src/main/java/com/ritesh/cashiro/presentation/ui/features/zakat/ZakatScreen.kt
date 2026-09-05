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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.ritesh.cashiro.presentation.ui.features.zakat.dashboard.ZakatDashboardViewModel
import com.ritesh.cashiro.presentation.ui.components.CustomTitleTopAppBar
import com.ritesh.cashiro.presentation.ui.components.CashiroCard
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Zakat breakdown (Option A — replaces the old manual-entry calculator).
 *
 * This screen computes NOTHING of its own: it is a read-only drill-down
 * of the exact state the Zakat dashboard derived from the user's real
 * Accounts + Assets ledger, so there is exactly one source of truth for
 * the zakat figures. The only editable values here are the gold/silver
 * gram prices (with live-fetch support and a manual-override badge) and
 * the shared nisab-standard / calendar-convention settings.
 *
 * The wealth inputs of the former standalone calculator were removed on
 * purpose: re-typed cash/gold/silver/investment/debt figures could drift
 * from the tracked data and produce a conflicting "zakat due" answer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZakatScreen(
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ZakatDashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshStatus by viewModel.metalRateRefreshStatus.collectAsStateWithLifecycle()
    val currencyCode = state.currencyCode

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.zakat_breakdown_title),
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
                        text = stringResource(R.string.zakat_breakdown_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(R.string.zakat_currency_note, currencyCode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ----- Wealth pool breakdown (read-only, mirrors the dashboard) -----
            item {
                BreakdownCard(state)
            }

            // ----- Nisab comparison -----
            item {
                NisabCard(state)
            }

            // ----- Hawl progress (pool mode detail) -----
            item {
                HawlCard(state)
            }

            // ----- Metal prices: live fetch + manual override -----
            item {
                PricesCard(
                    state = state,
                    refreshStatus = refreshStatus,
                    onGoldPriceChange = viewModel::setGoldPriceManual,
                    onSilverPriceChange = viewModel::setSilverPriceManual,
                    onRefreshRates = viewModel::refreshMetalRates,
                    onNisabMethodChange = viewModel::setNisabMethod,
                    onCalendarModeChange = viewModel::setCalendarMode
                )
            }
        }
    }
}

@Composable
private fun BreakdownCard(state: ZakatDashboardViewModel.UiState) {
    SectionCard {
        Text(
            text = stringResource(R.string.zakat_wealth_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.sm))

        val breakdown = state.breakdown
        if (!state.hasAnyData) {
            Text(
                text = stringResource(R.string.zakat_breakdown_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            BreakdownRow(
                stringResource(R.string.zakat_dash_cash),
                CurrencyFormatter.formatCurrency(breakdown.cash, state.currencyCode)
            )
            BreakdownRow(
                stringResource(R.string.zakat_dash_gold),
                CurrencyFormatter.formatCurrency(breakdown.gold, state.currencyCode)
            )
            BreakdownRow(
                stringResource(R.string.zakat_dash_silver),
                CurrencyFormatter.formatCurrency(breakdown.silver, state.currencyCode)
            )
            BreakdownRow(
                stringResource(R.string.zakat_dash_other_assets),
                CurrencyFormatter.formatCurrency(breakdown.otherAssets, state.currencyCode)
            )
            BreakdownRow(
                stringResource(R.string.zakat_breakdown_deductions),
                CurrencyFormatter.formatCurrency(breakdown.deductions, state.currencyCode)
            )
            BreakdownRow(
                stringResource(R.string.zakat_net_wealth),
                CurrencyFormatter.formatCurrency(breakdown.netWealth, state.currencyCode),
                emphasized = true
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(
                    R.string.zakat_breakdown_rate,
                    state.appliedRate.toPlainString() + "%"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.zakatDue.signum() > 0) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.zakat_due),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(state.zakatDue, state.currencyCode),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun NisabCard(state: ZakatDashboardViewModel.UiState) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.zakat_dash_nisab_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        stringResource(
                            if (state.aboveNisab) {
                                R.string.zakat_dash_above_nisab
                            } else {
                                R.string.zakat_dash_below_nisab
                            }
                        )
                    )
                }
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        BreakdownRow(
            stringResource(R.string.zakat_nisab_value),
            CurrencyFormatter.formatCurrency(state.appliedNisabValue, state.currencyCode)
        )
        BreakdownRow(
            stringResource(R.string.zakat_nisab_gold),
            CurrencyFormatter.formatCurrency(state.goldNisabValue, state.currencyCode)
        )
        BreakdownRow(
            stringResource(R.string.zakat_nisab_silver),
            CurrencyFormatter.formatCurrency(state.silverNisabValue, state.currencyCode)
        )
    }
}

@Composable
private fun HawlCard(state: ZakatDashboardViewModel.UiState) {
    SectionCard {
        Text(
            text = stringResource(R.string.zakat_dash_hawl_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.sm))

        if (state.crossingDate == null) {
            Text(
                text = stringResource(R.string.zakat_dash_no_active_hawl),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val fraction = if (state.hawlDaysInYear > 0) {
                (state.hawlDaysElapsed.toFloat() / state.hawlDaysInYear).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(
                    R.string.zakat_hawl_progress,
                    state.hawlDaysElapsed,
                    state.hawlDaysInYear
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = if (state.hawlComplete) {
                    stringResource(R.string.zakat_hawl_complete)
                } else {
                    stringResource(R.string.zakat_hawl_in_progress)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (state.hawlComplete) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(
                    R.string.zakat_dash_crossing_date,
                    state.crossingDate.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    )
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.projectedCompletionDate?.let { projected ->
                Text(
                    text = stringResource(
                        R.string.zakat_dash_projected_completion,
                        projected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PricesCard(
    state: ZakatDashboardViewModel.UiState,
    refreshStatus: ZakatDashboardViewModel.MetalRateRefreshStatus,
    onGoldPriceChange: (String) -> Unit,
    onSilverPriceChange: (String) -> Unit,
    onRefreshRates: () -> Unit,
    onNisabMethodChange: (ZakatCalculator.NisabMethod) -> Unit,
    onCalendarModeChange: (ZakatCalculator.CalendarMode) -> Unit
) {
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val zone = ZoneId.systemDefault()

    SectionCard {
        Text(
            text = stringResource(R.string.zakat_prices_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PriceField(
                label = stringResource(R.string.zakat_gold_price),
                value = state.goldPricePerGram,
                updatedAt = state.goldPriceUpdatedAt,
                isManual = state.goldPriceIsManual,
                timeFormatter = timeFormatter,
                zone = zone,
                onValueChange = onGoldPriceChange
            )
            PriceField(
                label = stringResource(R.string.zakat_silver_price),
                value = state.silverPricePerGram,
                updatedAt = state.silverPriceUpdatedAt,
                isManual = state.silverPriceIsManual,
                timeFormatter = timeFormatter,
                zone = zone,
                onValueChange = onSilverPriceChange
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (refreshStatus) {
                ZakatDashboardViewModel.MetalRateRefreshStatus.Idle -> {}
                ZakatDashboardViewModel.MetalRateRefreshStatus.Refreshing -> Text(
                    text = stringResource(R.string.zakat_rates_refreshing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ZakatDashboardViewModel.MetalRateRefreshStatus.Succeeded -> Text(
                    text = stringResource(R.string.zakat_rates_refreshed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                ZakatDashboardViewModel.MetalRateRefreshStatus.Failed -> Text(
                    text = stringResource(R.string.zakat_rates_refresh_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TextButton(
                onClick = onRefreshRates,
                enabled = refreshStatus !=
                    ZakatDashboardViewModel.MetalRateRefreshStatus.Refreshing
            ) {
                Text(stringResource(R.string.zakat_refresh_rates))
            }
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

        // Calendar convention (spec 4.3/8.2): LUNAR default; SOLAR switches
        // the hawl length AND the rate together — never mixed.
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.zakat_calendar_mode),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(
                selected = state.calendarMode == ZakatCalculator.CalendarMode.LUNAR,
                onClick = { onCalendarModeChange(ZakatCalculator.CalendarMode.LUNAR) },
                label = { Text(stringResource(R.string.zakat_calendar_lunar)) }
            )
            FilterChip(
                selected = state.calendarMode == ZakatCalculator.CalendarMode.SOLAR,
                onClick = { onCalendarModeChange(ZakatCalculator.CalendarMode.SOLAR) },
                label = { Text(stringResource(R.string.zakat_calendar_solar)) }
            )
        }
    }
}

@Composable
private fun PriceField(
    label: String,
    value: String,
    updatedAt: Long,
    isManual: Boolean,
    timeFormatter: DateTimeFormatter,
    zone: ZoneId,
    onValueChange: (String) -> Unit
) {
    Column {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isManual) {
                    stringResource(R.string.zakat_rates_manual)
                } else {
                    stringResource(R.string.zakat_rates_live)
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isManual) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = if (updatedAt != 0L) {
                    stringResource(
                        R.string.zakat_rates_last_updated,
                        Instant.ofEpochMilli(updatedAt)
                            .atZone(zone)
                            .format(timeFormatter)
                    )
                } else {
                    stringResource(R.string.zakat_rates_never_updated)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    value: String,
    emphasized: Boolean = false
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
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasized) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SectionCard(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    // Uses the shared CashiroCard language (theme shape + surfaceContainerLow)
    // so the Zakat breakdown matches the rest of the app.
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

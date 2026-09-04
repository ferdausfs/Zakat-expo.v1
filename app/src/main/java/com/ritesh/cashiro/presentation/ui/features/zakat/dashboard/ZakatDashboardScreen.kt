package com.ritesh.cashiro.presentation.ui.features.zakat.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritesh.cashiro.R
import com.ritesh.cashiro.domain.zakat.WealthPoolCalculator
import com.ritesh.cashiro.domain.zakat.ZakatCalculator
import com.ritesh.cashiro.presentation.ui.components.CustomTitleTopAppBar
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import com.ritesh.cashiro.presentation.ui.features.zakat.ZakatGradientProgressBar
import com.ritesh.cashiro.presentation.ui.features.zakat.ZakatSectionCard
import com.ritesh.cashiro.presentation.ui.features.zakat.ZakatSectionUi
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Zakat dashboard (Phase 2b): the Zakat tab's at-a-glance view.
 *
 * Shows the combined zakatable wealth pool (cash pulled automatically
 * from accounts + tracked assets), nisab status, the auto-detected
 * nisab-crossing date, hawl progress toward the projected completion
 * date, and — when the hawl is complete — the zakat due with a fully
 * transparent derivation. Quick links jump to the asset ledger, the
 * Phase 2a calculator and currency settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZakatDashboardScreen(
    onNavigateToAssets: () -> Unit,
    onNavigateToCalculator: () -> Unit,
    onNavigateToCurrencySettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ZakatDashboardViewModel = hiltViewModel(),
    onNavigateToUshr: () -> Unit = {},
    onNavigateToLivestock: () -> Unit = {},
    onNavigateToFitr: () -> Unit = {},
    onNavigateToPayments: () -> Unit = {},
    onNavigateToLiabilities: () -> Unit = {}
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
                hasBackButton = false
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

            // ----- Total zakatable wealth + breakdown -----
            item {
                ZakatSectionCard(title = stringResource(R.string.zakat_dash_wealth_title)) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    // Brand gradient on the hero amount — the Zakat accent
                    // within the shared design system.
                    Text(
                        text = CurrencyFormatter.formatCurrency(state.breakdown.total, currencyCode),
                        style = TextStyle(
                            fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            brush = ZakatSectionUi.brandGradient
                        )
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    BreakdownRow(
                        label = stringResource(R.string.zakat_dash_cash),
                        value = state.breakdown.cash,
                        currencyCode = currencyCode
                    )
                    BreakdownRow(
                        label = stringResource(R.string.zakat_dash_gold),
                        value = state.breakdown.gold,
                        currencyCode = currencyCode
                    )
                    BreakdownRow(
                        label = stringResource(R.string.zakat_dash_silver),
                        value = state.breakdown.silver,
                        currencyCode = currencyCode
                    )
                    BreakdownRow(
                        label = stringResource(R.string.zakat_dash_other_assets),
                        value = state.breakdown.otherAssets,
                        currencyCode = currencyCode
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.zakat_dash_cash_auto_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.breakdown.excluded.signum() > 0) {
                        BreakdownRow(
                            label = stringResource(R.string.zakat_excluded_line),
                            value = state.breakdown.excluded.negate(),
                            currencyCode = currencyCode
                        )
                    }
                    if (state.deductions.signum() > 0) {
                        BreakdownRow(
                            label = stringResource(R.string.zakat_deductions_line),
                            value = state.deductions.negate(),
                            currencyCode = currencyCode
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                        BreakdownRow(
                            label = stringResource(R.string.zakat_net_line),
                            value = state.breakdown.netWealth,
                            currencyCode = currencyCode,
                            bold = true
                        )
                    }
                }
            }

            // ----- Nisab status -----
            item {
                ZakatSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.zakat_dash_nisab_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        NisabBadge(above = state.aboveNisab)
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(
                            selected = state.nisabMethod == ZakatCalculator.NisabMethod.SILVER,
                            onClick = { viewModel.setNisabMethod(ZakatCalculator.NisabMethod.SILVER) },
                            label = { Text(stringResource(R.string.zakat_nisab_silver)) }
                        )
                        FilterChip(
                            selected = state.nisabMethod == ZakatCalculator.NisabMethod.GOLD,
                            onClick = { viewModel.setNisabMethod(ZakatCalculator.NisabMethod.GOLD) },
                            label = { Text(stringResource(R.string.zakat_nisab_gold)) }
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(
                            R.string.zakat_dash_nisab_value_line,
                            CurrencyFormatter.formatCurrency(state.appliedNisabValue, currencyCode)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val gap = state.breakdown.total - state.appliedNisabValue
                    Text(
                        text = if (state.aboveNisab) {
                            stringResource(
                                R.string.zakat_dash_gap_above,
                                CurrencyFormatter.formatCurrency(gap, currencyCode)
                            )
                        } else {
                            stringResource(
                                R.string.zakat_dash_gap_below,
                                CurrencyFormatter.formatCurrency(gap.abs(), currencyCode)
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(
                            R.string.zakat_dash_prices_line,
                            state.goldPricePerGram.ifBlank {
                                stringResource(R.string.zakat_dash_price_not_set)
                            },
                            state.silverPricePerGram.ifBlank {
                                stringResource(R.string.zakat_dash_price_not_set)
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onNavigateToCalculator) {
                        Text(stringResource(R.string.zakat_dash_edit_prices))
                    }
                }
            }

            // ----- Hawl -----
            item {
                ZakatSectionCard(title = stringResource(R.string.zakat_dash_hawl_title)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(
                            selected = state.hawlMode == ZakatDashboardViewModel.HawlMode.POOL,
                            onClick = { viewModel.setHawlMode(ZakatDashboardViewModel.HawlMode.POOL) },
                            label = { Text(stringResource(R.string.zakat_dash_mode_pool)) }
                        )
                        FilterChip(
                            selected = state.hawlMode == ZakatDashboardViewModel.HawlMode.PER_ASSET,
                            onClick = { viewModel.setHawlMode(ZakatDashboardViewModel.HawlMode.PER_ASSET) },
                            label = { Text(stringResource(R.string.zakat_dash_mode_per_asset)) }
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.md))
                    if (state.hawlMode == ZakatDashboardViewModel.HawlMode.POOL) {
                        PoolHawlSection(state, currencyCode)
                    } else {
                        PerAssetHawlSection(state, currencyCode)
                    }
                }
            }

            // ----- Zakat due (when hawl complete and above nisab) -----
            if (state.hawlComplete && state.aboveNisab && state.hawlMode == ZakatDashboardViewModel.HawlMode.POOL) {
                item {
                    ZakatSectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer, title = stringResource(R.string.zakat_dash_due_title)) {
                        Text(
                            text = CurrencyFormatter.formatCurrency(state.zakatDue, currencyCode),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = stringResource(R.string.zakat_dash_due_derivation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        for (line in state.dueLines) {
                            val label = when (line.label) {
                                "cash" -> stringResource(R.string.zakat_dash_cash)
                                "gold" -> stringResource(R.string.zakat_dash_gold)
                                "silver" -> stringResource(R.string.zakat_dash_silver)
                                "deductions" -> stringResource(R.string.zakat_deductions_line)
                                "net" -> stringResource(R.string.zakat_net_line)
                                else -> stringResource(R.string.zakat_dash_other_assets)
                            }
                            DueRow(
                                label = label,
                                amount = line.amount,
                                share = line.share,
                                currencyCode = currencyCode,
                                onContainer = true
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = stringResource(
                                R.string.zakat_rate_line,
                                state.appliedRate.multiply(java.math.BigDecimal(100))
                                    .stripTrailingZeros().toPlainString() + "%"
                            ) + " • " + stringResource(
                                if (state.calendarMode == ZakatCalculator.CalendarMode.SOLAR)
                                    R.string.zakat_calendar_solar else R.string.zakat_calendar_lunar
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // ----- Settings: calendar mode + madhhab + amanat (spec 7/8/10) -----
            item {
                ZakatSectionCard(title = stringResource(R.string.zakat_calendar_mode)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(
                            selected = state.calendarMode == ZakatCalculator.CalendarMode.LUNAR,
                            onClick = { viewModel.setCalendarMode(ZakatCalculator.CalendarMode.LUNAR) },
                            label = { Text(stringResource(R.string.zakat_calendar_lunar)) }
                        )
                        FilterChip(
                            selected = state.calendarMode == ZakatCalculator.CalendarMode.SOLAR,
                            onClick = { viewModel.setCalendarMode(ZakatCalculator.CalendarMode.SOLAR) },
                            label = { Text(stringResource(R.string.zakat_calendar_solar)) }
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(R.string.zakat_calendar_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.zakat_madhhab_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        items(com.ritesh.cashiro.domain.zakat.ZakatMadhhab.entries) { m ->
                            FilterChip(
                                selected = state.madhhab == m,
                                onClick = { viewModel.setMadhhab(m) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (m) {
                                                com.ritesh.cashiro.domain.zakat.ZakatMadhhab.MAINSTREAM -> R.string.zakat_madhhab_mainstream
                                                com.ritesh.cashiro.domain.zakat.ZakatMadhhab.HANAFI -> R.string.zakat_madhhab_hanafi
                                                com.ritesh.cashiro.domain.zakat.ZakatMadhhab.SHAFII -> R.string.zakat_madhhab_shafii
                                                com.ritesh.cashiro.domain.zakat.ZakatMadhhab.MALIKI -> R.string.zakat_madhhab_maliki
                                                com.ritesh.cashiro.domain.zakat.ZakatMadhhab.HANBALI -> R.string.zakat_madhhab_hanbali
                                            }
                                        )
                                    )
                                }
                            )
                        }
                    }
                    if (state.knownAccounts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = stringResource(R.string.zakat_asset_amanat),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        state.knownAccounts.forEach { (bank, last4) ->
                            val key = "$bank|$last4"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$bank ••$last4",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Switch(
                                    checked = key in state.amanatAccountKeys,
                                    onCheckedChange = { viewModel.toggleAmanatAccount(key) }
                                )
                            }
                        }
                    }
                }
            }

            // ----- Other zakat duties (spec 5/6/9/12 + 2.1) -----
            item {
                ZakatSectionCard(title = stringResource(R.string.zakat_modules_title)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(MODULE_LINKS) { link ->
                            when (link) {
                                "ushr" -> AssistChip(
                                    onClick = onNavigateToUshr,
                                    label = { Text(stringResource(R.string.zakat_modules_ushr)) }
                                )
                                "livestock" -> AssistChip(
                                    onClick = onNavigateToLivestock,
                                    label = { Text(stringResource(R.string.zakat_modules_livestock)) }
                                )
                                "fitr" -> AssistChip(
                                    onClick = onNavigateToFitr,
                                    label = { Text(stringResource(R.string.zakat_modules_fitr)) }
                                )
                                "payments" -> AssistChip(
                                    onClick = onNavigateToPayments,
                                    label = { Text(stringResource(R.string.zakat_modules_payments)) }
                                )
                                else -> AssistChip(
                                    onClick = onNavigateToLiabilities,
                                    label = { Text(stringResource(R.string.zakat_modules_liabilities)) }
                                )
                            }
                        }
                    }
                }
            }

            // ----- Quick links -----
            item {
                ZakatSectionCard(title = stringResource(R.string.zakat_dash_quick_links)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(QUICK_LINKS) { link ->
                            when (link) {
                                "assets" -> AssistChip(
                                    onClick = onNavigateToAssets,
                                    label = { Text(stringResource(R.string.zakat_dash_link_assets)) }
                                )
                                "calculator" -> AssistChip(
                                    onClick = onNavigateToCalculator,
                                    label = { Text(stringResource(R.string.zakat_dash_link_calculator)) }
                                )
                                else -> AssistChip(
                                    onClick = onNavigateToCurrencySettings,
                                    label = { Text(stringResource(R.string.zakat_dash_link_currency)) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    TextButton(onClick = onNavigateToAssets) {
                        Text(stringResource(R.string.zakat_dash_manage_assets))
                    }
                }
            }
        }
    }
}

private val QUICK_LINKS = listOf("assets", "calculator", "currency")
private val MODULE_LINKS = listOf("liabilities", "ushr", "livestock", "fitr", "payments")

@Composable
private fun PoolHawlSection(state: ZakatDashboardViewModel.UiState, currencyCode: String) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val start = state.crossingDate
    if (start == null) {
        Text(
            text = stringResource(R.string.zakat_dash_no_active_hawl),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.firstEverCrossingDate?.let { first ->
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.zakat_dash_first_crossing, first.format(dateFormatter)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Text(
        text = stringResource(
            R.string.zakat_dash_crossing_date,
            start.format(dateFormatter)
        ),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = stringResource(R.string.zakat_dash_crossing_auto),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(Spacing.md))

    val fraction = if (state.hawlDaysInYear > 0) {
        (state.hawlDaysElapsed.toFloat() / state.hawlDaysInYear).coerceIn(0f, 1f)
    } else 0f
    ZakatGradientProgressBar(
        fraction = fraction,
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
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
    state.projectedCompletionDate?.let { projected ->
        Text(
            text = stringResource(
                R.string.zakat_dash_projected_completion,
                projected.format(dateFormatter)
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (state.hawlComplete) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
    if (state.hawlComplete) {
        Text(
            text = stringResource(R.string.zakat_hawl_complete),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    } else if (state.breakdown.total > BigDecimal.ZERO) {
        Text(
            text = stringResource(
                R.string.zakat_dash_due_estimate,
                CurrencyFormatter.formatCurrency(
                    state.breakdown.total.multiply(ZakatCalculator.ZAKAT_RATE)
                        .setScale(2, java.math.RoundingMode.HALF_UP),
                    currencyCode
                )
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PerAssetHawlSection(state: ZakatDashboardViewModel.UiState, currencyCode: String) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    if (state.perAssetStatuses.isEmpty()) {
        Text(
            text = stringResource(R.string.zakat_dash_per_asset_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    for (item in state.perAssetStatuses) {
        val isCash = item.asset.id == ZakatDashboardViewModel.CASH_PSEUDO_ID
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isCash) {
                        stringResource(R.string.zakat_dash_cash)
                    } else {
                        item.asset.name.ifBlank { item.asset.type }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(item.value, currencyCode),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = stringResource(
                    R.string.zakat_dash_acquired_on,
                    item.asset.acquisitionDate.format(dateFormatter)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val fraction = if (item.daysInYear > 0) {
                (item.daysElapsed.toFloat() / item.daysInYear).coerceIn(0f, 1f)
            } else 0f
            ZakatGradientProgressBar(
                fraction = fraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .padding(vertical = 0.dp)
            )
            Text(
                text = if (item.hawlComplete) {
                    stringResource(
                        R.string.zakat_dash_per_asset_due,
                        CurrencyFormatter.formatCurrency(item.zakatDue, currencyCode)
                    )
                } else {
                    stringResource(
                        R.string.zakat_hawl_progress,
                        item.daysElapsed,
                        item.daysInYear
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            item.completionDate?.let { completion ->
                Text(
                    text = stringResource(
                        R.string.zakat_dash_projected_completion,
                        completion.format(dateFormatter)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
        }
    }
}

@Composable
private fun NisabBadge(above: Boolean) {
    val container = if (above) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val content = if (above) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Text(
            text = stringResource(
                if (above) R.string.zakat_dash_above_nisab else R.string.zakat_dash_below_nisab
            ),
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content
        )
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    value: BigDecimal,
    currencyCode: String,
    bold: Boolean = false
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
            fontWeight = if (bold) FontWeight.SemiBold else null,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = CurrencyFormatter.formatCurrency(value, currencyCode),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DueRow(
    label: String,
    amount: BigDecimal,
    share: BigDecimal,
    currencyCode: String,
    onContainer: Boolean
) {
    val labelColor = if (onContainer) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val valueColor = if (onContainer) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor
        )
        Text(
            text = CurrencyFormatter.formatCurrency(amount, currencyCode) + " → " +
                CurrencyFormatter.formatCurrency(share, currencyCode),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}



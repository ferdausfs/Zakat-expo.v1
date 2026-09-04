package com.ritesh.cashiro.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ritesh.cashiro.R
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.hazeEffect
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Scan-range options shown before every manual SMS scan (spec 13.1).
 *
 * Each option resolves to a [fromMs, toMs] epoch-milli window that is passed
 * to [com.ritesh.cashiro.worker.OptimizedSmsReaderWorker] and applied at the
 * SMS provider query level, so only messages inside the selected window are
 * ever fetched from the inbox.
 */
enum class SmsScanRangeOption(val titleRes: Int, val subtitleRes: Int) {
    LAST_7_DAYS(R.string.scan_range_7_days, R.string.scan_range_7_days_sub),
    LAST_1_MONTH(R.string.scan_range_1_month, R.string.scan_range_1_month_sub),
    LAST_3_MONTHS(R.string.scan_range_3_months, R.string.scan_range_3_months_sub),
    CUSTOM(R.string.scan_range_custom, R.string.scan_range_custom_sub),
    ALL_TIME(R.string.scan_range_all_time, R.string.scan_range_all_time_sub)
}

/** Start-of-day millis for [date]. */
private fun startOfDayMillis(date: java.time.LocalDate): Long {
    val cal = Calendar.getInstance().apply {
        set(date.year, date.monthValue - 1, date.dayOfMonth, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/** End-of-day millis for [date]. */
private fun endOfDayMillis(date: java.time.LocalDate): Long =
    startOfDayMillis(date) + TimeUnit.DAYS.toMillis(1) - 1

/**
 * Popup shown when the user taps "Scan SMS" — BEFORE the scan starts —
 * letting the user choose how far back to scan (spec 13.1). Default is
 * "Last 1 month". "All time" carries a visible warning for large inboxes.
 *
 * onScan receives the resolved epoch-millis window (from <= to). For
 * [SmsScanRangeOption.ALL_TIME] the window is a 10-year lookback, matching
 * the existing all-time scan semantics.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun ScanRangePickerDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onScan: (fromMs: Long, toMs: Long) -> Unit,
    blurEffects: Boolean = false,
    hazeState: HazeState = remember { HazeState() }
) {
    if (!isVisible) return

    // Hoisted so no composable calls happen inside the haze lambda.
    val dialogContainer = if (blurEffects)
        MaterialTheme.colorScheme.surfaceContainerLow.copy(0.5f)
    else MaterialTheme.colorScheme.surfaceContainerLow
    val hazeTint = MaterialTheme.colorScheme.surfaceContainerLow
    val dialogBlurModifier = if (blurEffects) Modifier.hazeEffect(
        state = hazeState,
        block = fun HazeEffectScope.() {
            style = HazeDefaults.style(
                backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
                tint = HazeDefaults.tint(hazeTint),
                blurRadius = 20.dp,
                noiseFactor = -1f,
            )
            blurredEdgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded
        }
    ) else Modifier

    var selected by remember { mutableStateOf(SmsScanRangeOption.LAST_1_MONTH) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var customStart by remember {
        mutableStateOf(java.time.LocalDate.now().minusDays(30))
    }
    var customEnd by remember { mutableStateOf(java.time.LocalDate.now()) }

    if (showStartDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startOfDayMillis(customStart)
        )
        DatePicker(
            onDismiss = { showStartDatePicker = false },
            onConfirm = {
                state.selectedDateMillis?.let { millis ->
                    customStart = java.time.Instant.ofEpochMilli(millis)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    if (customEnd.isBefore(customStart)) customEnd = customStart
                }
                showStartDatePicker = false
            },
            datePickerState = state
        )
    }
    if (showEndDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startOfDayMillis(customEnd)
        )
        DatePicker(
            onDismiss = { showEndDatePicker = false },
            onConfirm = {
                state.selectedDateMillis?.let { millis ->
                    val picked = java.time.Instant.ofEpochMilli(millis)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    if (!picked.isBefore(customStart)) customEnd = picked
                }
                showEndDatePicker = false
            },
            datePickerState = state
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.scan_range_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SmsScanRangeOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selected = option }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == option,
                            onClick = { selected = option }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(option.titleRes),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (option == selected) {
                                when (option) {
                                    SmsScanRangeOption.CUSTOM -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement =
                                                Arrangement.spacedBy(8.dp)
                                        ) {
                                            TextButton(
                                                onClick = { showStartDatePicker = true }
                                            ) {
                                                Text(
                                                    stringResource(
                                                        R.string.scan_range_start_date,
                                                        customStart.toString()
                                                    )
                                                )
                                            }
                                            TextButton(
                                                onClick = { showEndDatePicker = true }
                                            ) {
                                                Text(
                                                    stringResource(
                                                        R.string.scan_range_end_date,
                                                        customEnd.toString()
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    SmsScanRangeOption.ALL_TIME -> {
                                        Text(
                                            text = stringResource(
                                                R.string.scan_range_all_time_warning
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = stringResource(option.subtitleRes),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val today = java.time.LocalDate.now()
                    val (from, to) = when (selected) {
                        SmsScanRangeOption.LAST_7_DAYS ->
                            startOfDayMillis(today.minusDays(7)) to endOfDayMillis(today)
                        SmsScanRangeOption.LAST_1_MONTH ->
                            startOfDayMillis(today.minusDays(30)) to endOfDayMillis(today)
                        SmsScanRangeOption.LAST_3_MONTHS ->
                            startOfDayMillis(today.minusDays(91)) to endOfDayMillis(today)
                        SmsScanRangeOption.CUSTOM ->
                            startOfDayMillis(customStart) to endOfDayMillis(customEnd)
                        SmsScanRangeOption.ALL_TIME ->
                            startOfDayMillis(today.minusDays(3650)) to endOfDayMillis(today)
                    }
                    onScan(from, to)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(text = stringResource(R.string.scan_range_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        containerColor = dialogContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(dialogBlurModifier),
        shape = RoundedCornerShape(16.dp)
    )
}

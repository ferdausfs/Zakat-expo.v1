package com.ritesh.cashiro.presentation.ui.features.zakat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ritesh.cashiro.presentation.ui.components.CashiroCard
import com.ritesh.cashiro.presentation.ui.components.SectionHeader
import com.ritesh.cashiro.presentation.ui.theme.Spacing

/**
 * Zakat section × shared design-system bridge.
 *
 * The Zakat feature reuses the exact same card/header/progress language as
 * the rest of the app (CashiroCard, SectionHeader, MaterialTheme surfaces)
 * while allowing the brand teal→green gradient from the launcher emblem to
 * appear as a tasteful highlight (gradient amount text, gradient progress
 * fill). This keeps the Zakat screens feeling like one cohesive app instead
 * of a separate design system.
 */
object ZakatSectionUi {
    /** Brand accent taken from the launcher emblem gradient. */
    val BRAND_TEAL = Color(0xFF2DD4BF)
    val BRAND_GREEN = Color(0xFF22C55E)

    /** Teal→green brand gradient (logo emblem colors). */
    val brandGradient: Brush
        get() = Brush.linearGradient(listOf(BRAND_TEAL, BRAND_GREEN))
}

/**
 * Section card used across all Zakat screens. Built on the shared
 * [CashiroCard] (surfaceContainerLow, theme shape) with an optional
 * section title rendered in the app's [SectionHeader] style.
 */
@Composable
fun ZakatSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    CashiroCard(modifier = modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = containerColor)) {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.dp)) {
            if (title != null) {
                SectionHeader(title = title, style = MaterialTheme.typography.titleMedium)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = Spacing.xs))
            }
            content()
        }
    }
}

/**
 * Hawl progress bar with the brand teal→green gradient fill. Matches the
 * height/radius of the app's LinearProgressIndicator usage but carries the
 * Zakat accent.
 */
@Composable
fun ZakatGradientProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier
) {
    val clamped = fraction.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier = modifier.fillMaxWidth()) {
        val barHeight = size.height
        val radius = CornerRadius(barHeight / 2f, barHeight / 2f)
        // Track
        drawRoundRect(color = track, cornerRadius = radius)
        // Gradient fill
        if (clamped > 0f) {
            val fillWidth = size.width * clamped
            drawRoundRect(
                brush = ZakatSectionUi.brandGradient,
                topLeft = Offset.Zero,
                size = Size(fillWidth, barHeight),
                cornerRadius = radius
            )
        }
    }
}

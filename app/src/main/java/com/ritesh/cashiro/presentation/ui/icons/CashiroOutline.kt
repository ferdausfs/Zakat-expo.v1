package com.ritesh.cashiro.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.CashiroOutline: ImageVector
    get() {
        if (_CashiroOutline != null) {
            return _CashiroOutline!!
        }
        _CashiroOutline = ImageVector.Builder(
            name = "CashiroOutline",
            defaultWidth = 192.dp,
            defaultHeight = 192.dp,
            viewportWidth = 192f,
            viewportHeight = 192f
        ).apply {
            // Kufic-stroke diamond ring outline.
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 0f,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 8.993004f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(96f, 36f)
                lineTo(156f, 96f)
                lineTo(96f, 156f)
                lineTo(36f, 96f)
                close()
            }
            // Inner woven diamond.
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 4.993004f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(96f, 66f)
                lineTo(126f, 96f)
                lineTo(96f, 126f)
                lineTo(66f, 96f)
                close()
            }
            // Small diamond accent at the center.
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 3.5f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(96f, 84f)
                lineTo(108f, 96f)
                lineTo(96f, 108f)
                lineTo(84f, 96f)
                close()
            }
        }.build()

        return _CashiroOutline!!
    }

@Suppress("ObjectPropertyName")
private var _CashiroOutline: ImageVector? = null

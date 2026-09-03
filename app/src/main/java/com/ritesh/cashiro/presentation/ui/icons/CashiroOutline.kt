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
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 0f,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 8.993004f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(92.06f, 28.12f)
                arcTo(68.00f, 68.00f, -0f, isMoreThanHalf = true, isPositiveArc = false, 146.63f, 130.44f)
                arcTo(58.00f, 58.00f, -0f, isMoreThanHalf = false, isPositiveArc = true, 92.06f, 28.12f)
                close()
            }
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 8.993004f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(126.00f, 44.00f)
                lineTo(130.80f, 65.20f)
                lineTo(152.00f, 70.00f)
                lineTo(130.80f, 74.80f)
                lineTo(126.00f, 96.00f)
                lineTo(121.20f, 74.80f)
                lineTo(100.00f, 70.00f)
                lineTo(121.20f, 65.20f)
                close()
            }
        }.build()

        return _CashiroOutline!!
    }

@Suppress("ObjectPropertyName")
private var _CashiroOutline: ImageVector? = null

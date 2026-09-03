package com.ritesh.cashiro.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Iconax.Cashiro: ImageVector
    get() {
        if (_Cashiro != null) {
            return _Cashiro!!
        }
        _Cashiro = ImageVector.Builder(
            name = "Cashiro",
            defaultWidth = 192.dp,
            defaultHeight = 192.dp,
            viewportWidth = 192f,
            viewportHeight = 192f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.Black),
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(92.06f, 28.12f)
                arcTo(68.00f, 68.00f, -0f, isMoreThanHalf = true, isPositiveArc = false, 146.63f, 130.44f)
                arcTo(58.00f, 58.00f, -0f, isMoreThanHalf = false, isPositiveArc = true, 92.06f, 28.12f)
                close()
            }
            path(
                fill = SolidColor(Color.White),
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

        return _Cashiro!!
    }

@Suppress("ObjectPropertyName")
private var _Cashiro: ImageVector? = null

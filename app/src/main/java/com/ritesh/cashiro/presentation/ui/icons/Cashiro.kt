package com.ritesh.cashiro.presentation.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
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
            // Kufic-stroke diamond ring (outer diamond with an inner diamond
            // cut out, even-odd) rendered as a filled ring shape.
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1.0f,
                pathFillType = PathFillType.EvenOdd,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(96f, 36f)
                lineTo(156f, 96f)
                lineTo(96f, 156f)
                lineTo(36f, 96f)
                close()
                moveTo(96f, 62f)
                lineTo(130f, 96f)
                lineTo(96f, 130f)
                lineTo(62f, 96f)
                close()
            }
            // Small diamond accent at the center.
            path(
                fill = SolidColor(Color.White),
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

        return _Cashiro!!
    }

@Suppress("ObjectPropertyName")
private var _Cashiro: ImageVector? = null

package com.github.barriosnahuel.vossosunboton.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object AppIcons {
    val Pause: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Pause",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(6.0f, 19.0f)
                    horizontalLineToRelative(4.0f)
                    lineTo(10.0f, 5.0f)
                    lineTo(6.0f, 5.0f)
                    verticalLineToRelative(14.0f)
                    close()
                    moveTo(14.0f, 5.0f)
                    verticalLineToRelative(14.0f)
                    horizontalLineToRelative(4.0f)
                    lineTo(18.0f, 5.0f)
                    horizontalLineToRelative(-4.0f)
                    close()
                }
            }.build()
    }

    val ViewComfyAlt: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "ViewComfyAlt",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(20.0f, 4.0f)
                    horizontalLineTo(4.0f)
                    curveTo(2.9f, 4.0f, 2.0f, 4.9f, 2.0f, 6.0f)
                    verticalLineToRelative(12.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(16.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineTo(6.0f)
                    curveTo(22.0f, 4.9f, 21.1f, 4.0f, 20.0f, 4.0f)
                    close()
                    moveTo(11.0f, 17.0f)
                    horizontalLineTo(7.0f)
                    verticalLineToRelative(-4.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineTo(17.0f)
                    close()
                    moveTo(11.0f, 11.0f)
                    horizontalLineTo(7.0f)
                    verticalLineTo(7.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineTo(11.0f)
                    close()
                    moveTo(17.0f, 17.0f)
                    horizontalLineToRelative(-4.0f)
                    verticalLineToRelative(-4.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineTo(17.0f)
                    close()
                    moveTo(17.0f, 11.0f)
                    horizontalLineToRelative(-4.0f)
                    verticalLineTo(7.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineTo(11.0f)
                    close()
                }
            }.build()
    }
}

/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

public object AppIcons {
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

    val PushPin: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "PushPin",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(16f, 9f)
                    verticalLineTo(4f)
                    horizontalLineToRelative(1f)
                    curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                    reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                    horizontalLineTo(7f)
                    curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                    reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                    horizontalLineToRelative(1f)
                    verticalLineToRelative(5f)
                    curveToRelative(0f, 1.66f, -1.34f, 3f, -3f, 3f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(5.97f)
                    verticalLineToRelative(7f)
                    lineToRelative(1f, 1f)
                    lineToRelative(1f, -1f)
                    verticalLineToRelative(-7f)
                    horizontalLineTo(19f)
                    verticalLineToRelative(-2f)
                    curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
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

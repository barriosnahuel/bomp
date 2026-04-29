/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
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

    val PushPinOutlined: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "PushPinOutlined",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
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
                    // Inner rectangle: creates the hollow pin-body via EvenOdd
                    moveTo(14f, 9f)
                    horizontalLineToRelative(-4f)
                    verticalLineTo(4f)
                    horizontalLineToRelative(4f)
                    verticalLineTo(9f)
                    close()
                }
            }.build()
    }

    val VolumeUp: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "VolumeUp",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(3f, 9f)
                    verticalLineToRelative(6f)
                    horizontalLineToRelative(4f)
                    lineToRelative(5f, 5f)
                    verticalLineTo(4f)
                    lineTo(7f, 9f)
                    horizontalLineTo(3f)
                    close()
                    moveTo(16.5f, 12f)
                    curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                    verticalLineToRelative(8.05f)
                    curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
                    close()
                    moveTo(14f, 3.23f)
                    verticalLineToRelative(2.06f)
                    curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.71f)
                    reflectiveCurveToRelative(-2.11f, 5.85f, -5f, 6.71f)
                    verticalLineToRelative(2.06f)
                    curveToRelative(4.01f, -0.91f, 7f, -4.49f, 7f, -8.77f)
                    reflectiveCurveToRelative(-2.99f, -7.86f, -7f, -8.77f)
                    close()
                }
            }.build()
    }

    val Description: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Description",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(14f, 2f)
                    horizontalLineTo(6f)
                    curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                    lineTo(4f, 20f)
                    curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
                    horizontalLineTo(18f)
                    curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                    verticalLineTo(8f)
                    lineToRelative(-6f, -6f)
                    close()
                    moveTo(16f, 18f)
                    horizontalLineTo(8f)
                    verticalLineToRelative(-2f)
                    horizontalLineToRelative(8f)
                    verticalLineToRelative(2f)
                    close()
                    moveTo(16f, 14f)
                    horizontalLineTo(8f)
                    verticalLineToRelative(-2f)
                    horizontalLineToRelative(8f)
                    verticalLineToRelative(2f)
                    close()
                    moveTo(13f, 9f)
                    verticalLineTo(3.5f)
                    lineTo(18.5f, 9f)
                    horizontalLineTo(13f)
                    close()
                }
            }.build()
    }

    val PrivacyTip: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "PrivacyTip",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                    moveTo(12f, 2f)
                    lineTo(4f, 5f)
                    verticalLineToRelative(6f)
                    curveToRelative(0f, 5.55f, 3.5f, 10.74f, 8f, 12f)
                    curveToRelative(4.5f, -1.26f, 8f, -6.45f, 8f, -12f)
                    verticalLineTo(5f)
                    lineTo(12f, 2f)
                    close()
                    moveTo(11f, 7f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(-2f)
                    close()
                    moveTo(11f, 11f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(6f)
                    horizontalLineToRelative(-2f)
                    close()
                }
            }.build()
    }

    val Shield: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Shield",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(12f, 2f)
                    lineTo(4f, 5f)
                    verticalLineToRelative(6f)
                    curveToRelative(0f, 5.55f, 3.5f, 10.74f, 8f, 12f)
                    curveToRelative(4.5f, -1.26f, 8f, -6.45f, 8f, -12f)
                    verticalLineTo(5f)
                    lineTo(12f, 2f)
                    close()
                }
            }.build()
    }

    val Code: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Code",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(9.4f, 16.6f)
                    lineTo(4.8f, 12f)
                    lineToRelative(4.6f, -4.6f)
                    lineTo(8f, 6f)
                    lineToRelative(-6f, 6f)
                    lineToRelative(6f, 6f)
                    lineToRelative(1.4f, -1.4f)
                    close()
                    moveTo(14.6f, 16.6f)
                    lineToRelative(4.6f, -4.6f)
                    lineToRelative(-4.6f, -4.6f)
                    lineTo(16f, 6f)
                    lineToRelative(6f, 6f)
                    lineToRelative(-6f, 6f)
                    lineToRelative(-1.4f, -1.4f)
                    close()
                }
            }.build()
    }

    val OpenInNew: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "OpenInNew",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(19f, 19f)
                    horizontalLineTo(5f)
                    verticalLineTo(5f)
                    horizontalLineToRelative(7f)
                    verticalLineTo(3f)
                    horizontalLineTo(5f)
                    curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                    verticalLineToRelative(14f)
                    curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                    horizontalLineToRelative(14f)
                    curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                    verticalLineToRelative(-7f)
                    horizontalLineToRelative(-2f)
                    verticalLineToRelative(7f)
                    close()
                    moveTo(14f, 3f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(3.59f)
                    lineToRelative(-9.83f, 9.83f)
                    lineToRelative(1.41f, 1.41f)
                    lineTo(19f, 6.41f)
                    verticalLineTo(10f)
                    horizontalLineToRelative(2f)
                    verticalLineTo(3f)
                    horizontalLineToRelative(-7f)
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

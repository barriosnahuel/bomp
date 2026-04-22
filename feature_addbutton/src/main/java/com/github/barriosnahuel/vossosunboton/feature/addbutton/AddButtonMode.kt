/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.net.Uri
import com.github.barriosnahuel.vossosunboton.model.Sound

sealed class AddButtonMode {
    data class Create(
        val uri: Uri,
    ) : AddButtonMode()

    data class Edit(
        val sound: Sound,
    ) : AddButtonMode()
}

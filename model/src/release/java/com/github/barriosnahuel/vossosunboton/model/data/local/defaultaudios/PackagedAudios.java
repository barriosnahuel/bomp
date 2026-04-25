/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.model.data.local.defaultaudios;

import android.content.Context;

import androidx.annotation.NonNull;

import com.github.barriosnahuel.vossosunboton.model.Sound;

import java.util.Collections;
import java.util.List;

public final class PackagedAudios {

    private PackagedAudios() {
        // Do nothing.
    }

    /**
     * @param context The execution context.
     * @return an empty list — bundled audio is not included in release builds.
     */
    public static List<Sound> get(@NonNull final Context context) {
        return Collections.emptyList();
    }
}

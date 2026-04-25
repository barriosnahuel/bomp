/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.model.data.local.defaultaudios;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.github.barriosnahuel.vossosunboton.model.R;
import com.github.barriosnahuel.vossosunboton.model.Sound;

import java.util.ArrayList;
import java.util.List;

public final class PackagedAudios {

    private PackagedAudios() {
        // Do nothing.
    }

    /**
     * @param context The execution context.
     * @return a list of sample audios packaged within the app, or an empty list if no audio files
     * have been placed in the debug raw resource directory.
     */
    public static List<Sound> get(@NonNull final Context context) {
        final List<Sound> files = new ArrayList<>(40);

        addIfPresent(context, files, "model_sample_button_podemosactivar",    R.string.model_sample_button_activar_edited);
        addIfPresent(context, files, "model_sample_button_activar",           R.string.model_sample_button_activar);
        addIfPresent(context, files, "model_sample_button_ambulancia",        R.string.model_sample_button_ambulancia);
        addIfPresent(context, files, "model_sample_button_banfield",          R.string.model_sample_button_banfield);
        addIfPresent(context, files, "model_sample_button_campito",           R.string.model_sample_button_campito);
        addIfPresent(context, files, "model_sample_button_careta",            R.string.model_sample_button_careta);
        addIfPresent(context, files, "model_sample_button_chimichimi",        R.string.model_sample_button_chimichimi);
        addIfPresent(context, files, "model_sample_button_chiquichiqui",      R.string.model_sample_button_chiquichiqui);
        addIfPresent(context, files, "model_sample_button_corrientes",        R.string.model_sample_button_corrientes);
        addIfPresent(context, files, "model_sample_button_crosscountry",      R.string.model_sample_button_crosscountry);
        addIfPresent(context, files, "model_sample_button_derecha",           R.string.model_sample_button_derecha);
        addIfPresent(context, files, "model_sample_button_dondeestan",        R.string.model_sample_button_dondeestan);
        addIfPresent(context, files, "model_sample_button_dondeestan2",       R.string.model_sample_button_dondeestan2);
        addIfPresent(context, files, "model_sample_button_dondeestas",        R.string.model_sample_button_dondeestas);
        addIfPresent(context, files, "model_sample_button_estamosyendo",      R.string.model_sample_button_estamosyendo);
        addIfPresent(context, files, "model_sample_button_guardaconcramer",   R.string.model_sample_button_guardaconcramer);
        addIfPresent(context, files, "model_sample_button_guardaeltaxi",      R.string.model_sample_button_guardaeltaxi);
        addIfPresent(context, files, "model_sample_button_izquierda",         R.string.model_sample_button_izquierda);
        addIfPresent(context, files, "model_sample_button_izquierdaizquierda", R.string.model_sample_button_izquierdaizquierda);
        addIfPresent(context, files, "model_sample_button_lacamionetaimposible", R.string.model_sample_button_lacamionetaimposible);
        addIfPresent(context, files, "model_sample_button_jajajaja",          R.string.model_sample_button_jajaja);
        addIfPresent(context, files, "model_sample_button_meseguis",          R.string.model_sample_button_meseguis);
        addIfPresent(context, files, "model_sample_button_nahuuu",            R.string.model_sample_button_nahu);
        addIfPresent(context, files, "model_sample_button_nochedesexo",       R.string.model_sample_button_nochedesexo);
        addIfPresent(context, files, "model_sample_button_llegamosenochominutos", R.string.model_sample_button_ochominutos);
        addIfPresent(context, files, "model_sample_button_otrotema",          R.string.model_sample_button_otrotema);
        addIfPresent(context, files, "model_sample_button_pampayvinedos",     R.string.model_sample_button_pampayvinedos);
        addIfPresent(context, files, "model_sample_button_parademandarmsjs",  R.string.model_sample_button_parademandarmsjs);
        addIfPresent(context, files, "model_sample_button_pasaloverdeoamarillo", R.string.model_sample_button_pasaloenverdeoamarillo);
        addIfPresent(context, files, "model_sample_button_pelotudomarchaatras", R.string.model_sample_button_pelotudomarchaatras);
        addIfPresent(context, files, "model_sample_button_pepa",              R.string.model_sample_button_pepa);
        addIfPresent(context, files, "model_sample_button_pickypicky",        R.string.model_sample_button_pickypicky);
        addIfPresent(context, files, "model_sample_button_pistera",           R.string.model_sample_button_pistera);
        addIfPresent(context, files, "model_sample_button_poracahastaelsemaforo", R.string.model_sample_button_poracahastaelsemaforo);
        addIfPresent(context, files, "model_sample_button_poronga",           R.string.model_sample_button_poronga);
        addIfPresent(context, files, "model_sample_button_quepasaconnahu",    R.string.model_sample_button_quepasaconnahu);
        addIfPresent(context, files, "model_sample_button_quierofiesta",      R.string.model_sample_button_quierofiesta);
        addIfPresent(context, files, "model_sample_button_radio",             R.string.model_sample_button_radio);
        addIfPresent(context, files, "model_sample_button_tresminutos",       R.string.model_sample_button_tresminutos);
        addIfPresent(context, files, "model_sample_button_todosimulado",      R.string.model_sample_button_todosimulado);
        addIfPresent(context, files, "model_sample_button_trecientosocholacon", R.string.model_sample_button_trescientosocholacon);
        addIfPresent(context, files, "model_sample_button_unahorahaciendoluces", R.string.model_sample_button_unahorahaciendoluces);
        addIfPresent(context, files, "model_sample_button_verdeoamarillo",    R.string.model_sample_button_verdeoamarillo);

        return files;
    }

    private static void addIfPresent(
            @NonNull final Context context,
            @NonNull final List<Sound> out,
            @NonNull final String rawName,
            @StringRes final int nameResId
    ) {
        final int rawResId = context.getResources().getIdentifier(rawName, "raw", context.getPackageName());
        if (rawResId != 0) {
            out.add(new Sound(context.getString(nameResId), rawResId));
        }
    }
}

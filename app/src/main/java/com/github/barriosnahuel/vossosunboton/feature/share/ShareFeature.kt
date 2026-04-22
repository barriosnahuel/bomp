/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.github.barriosnahuel.vossosunboton.BuildConfig
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.file.copy
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.model.Sound
import timber.log.Timber
import java.io.FileOutputStream

internal interface ShareFeature {
    /**
     * @throws IllegalStateException when any required parameter is `null`
     */
    fun share(
        context: Context,
        sound: Sound,
    )

    companion object {
        val instance: ShareFeature by lazy { ShareFeatureImpl() }
    }
}

private class ShareFeatureImpl : ShareFeature {
    private val authority = BuildConfig.APPLICATION_ID + ".fileprovider"

    /**
     * For more info check
     * [developer.android.com/training/secure-file-sharing/setup-sharing](https://developer.android.com/training/secure-file-sharing/setup-sharing)
     */
    override fun share(
        context: Context,
        sound: Sound,
    ) {
        Timber.d("Trying to share button: %s", sound.name)

        val buttonFileContentUri = getContentUriForSound(sound, context)
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.type = "audio/*"
        shareIntent.putExtra(Intent.EXTRA_STREAM, buttonFileContentUri)
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        Timber.d(
            "Starting disambiguation window for: %s: contentUri=%s; URI=%s; rawResId=%s;",
            sound.name,
            buttonFileContentUri,
            sound.file,
            sound.rawRes,
        )

        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.app_share_chooser_title)))
    }

    private fun getContentUriForSound(
        sound: Sound,
        context: Context,
    ): Uri? {
        val fileForSharing =
            when (sound.file) {
                null -> {
                    check(sound.rawRes != 0) { "Either file URI or raw resource ID must exist on a given sound" }

                    val rawResourceInputStream = context.resources.openRawResource(sound.rawRes)

                    val fileForSharing = getFile(context, sound.name + ".mp3")
                    if (fileForSharing.exists()) {
                        Timber.d("Packaged audio already copied to share directory: %s", fileForSharing)
                    } else {
                        Timber.d("Packaged audio is gonna be copied to share directory: %s", fileForSharing)
                        copy(rawResourceInputStream, FileOutputStream(fileForSharing))
                    }

                    fileForSharing
                }
                else -> getFile(context, sound.file!!)
            }

        return try {
            FileProvider.getUriForFile(context, authority, fileForSharing)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Button content uri couldn't be created.", e)
        }
    }
}

package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.net.Uri
import com.github.barriosnahuel.vossosunboton.commons.file.copy
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundDao
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.jetbrains.annotations.NotNull
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * Actually persists the button into storage.
 */
interface AddButtonFeature {
    /**
     * @param context The execution context.
     * @param name Name of the button.
     * @param uri Sound's location.
     */
    fun saveNewButtonAsync(
        context: @NotNull Context,
        name: String,
        uri: String,
    ): Deferred<Int>

    companion object {
        val instance: AddButtonFeature by lazy { AddButtonFeatureImpl() }
    }
}

private class AddButtonFeatureImpl : AddButtonFeature {
    override fun saveNewButtonAsync(
        context: Context,
        name: String,
        uri: String,
    ): Deferred<Int> {
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "$sanitizedName-${System.currentTimeMillis()}.mp3"
        val targetFile = getFile(context, fileName)

        @OptIn(DelicateCoroutinesApi::class)
        return GlobalScope.async(Dispatchers.IO) {
            var feedbackMessage = com.github.barriosnahuel.vossosunboton.R.string.app_feedback_generic_error_contact_support
            try {
                FileOutputStream(targetFile).use { fileOutputStream ->
                    context.contentResolver.openInputStream(Uri.parse(uri)).use { inputStream ->
                        if (inputStream == null) {
                            Timber.e("Input stream obtained from the specified content URI is null: %s", uri)
                        } else {
                            copy(inputStream, fileOutputStream)
                            SoundDao().save(context, Sound(name, fileName))

                            feedbackMessage = R.string.feature_addbutton_feedback_saved_ok
                        }
                    }
                }
            } catch (e: FileNotFoundException) {
                Timber.e("Can't create new button's path")
            } catch (e: IOException) {
                Timber.e("Can't copy original audio")
            }

            feedbackMessage
        }
    }
}

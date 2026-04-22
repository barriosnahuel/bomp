/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
@file:JvmName("FileUtils")

package com.github.barriosnahuel.vossosunboton.commons.file

import android.content.Context
import android.os.Environment
import androidx.annotation.NonNull
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

private const val INPUT_STREAM_READ_BUFFER_SIZE = 1024

/**
 * Look for the given `fileName` at the [Environment.DIRECTORY_MUSIC] dir.
 *
 * @param context The execution context.
 * @param fileName The name of the file (`xxx.extension`).
 * @return A real [File] object.
 */
fun getFile(
    @NonNull context: Context,
    @NonNull fileName: String,
): File = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName)

/**
 * @param inputStream The input stream to copy.
 * @param fileOutputStream The output stream to use.
 * @throws IOException either calling [InputStream.read] or [FileOutputStream.write], or even when closing those streams.
 */
@Throws(IOException::class)
fun copy(
    @NonNull inputStream: InputStream,
    @NonNull fileOutputStream: FileOutputStream,
) {
    inputStream.copyTo(fileOutputStream, INPUT_STREAM_READ_BUFFER_SIZE)

    inputStream.close()
    fileOutputStream.close()
}

/**
 * @param context The execution context.
 * @param file The file name to delete.
 * @see getFile
 */
fun deleteFile(
    @NonNull context: Context,
    @NonNull file: String,
): Boolean {
    val theFile = getFile(context, file)

    val deleted: Boolean
    if (theFile.exists()) {
        deleted = theFile.delete()
        if (deleted) {
            Timber.i("File successfully deleted from file system: %s", file)
        } else {
            Timber.e("File could NOT be deleted from file system: %s", theFile.toString())
        }
    } else {
        deleted = false
    }
    return deleted
}

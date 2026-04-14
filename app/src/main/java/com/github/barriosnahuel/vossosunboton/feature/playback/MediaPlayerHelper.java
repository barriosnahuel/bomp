package com.github.barriosnahuel.vossosunboton.feature.playback;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;

import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker;
import com.github.barriosnahuel.vossosunboton.commons.file.FileUtils;

import java.io.FileDescriptor;
import java.io.IOException;

public final class MediaPlayerHelper {

    private MediaPlayerHelper() {
        // Do nothing.
    }

    /**
     * @param context     The execution context.
     * @param mediaPlayer The media player to setup.
     * @param file        The path of the sound's file.
     * @return <code>true</code> when call to {@link MediaPlayer#setDataSource(Context, Uri)} is ok.
     */
    public static boolean setupSoundSource(@NonNull final Context context,
                                           @NonNull final MediaPlayer mediaPlayer,
                                           @NonNull final String file) throws IOException {

        final Uri soundFileUri = Uri.fromFile(FileUtils.getFile(context, file));
        mediaPlayer.reset();
        mediaPlayer.setDataSource(context, soundFileUri);
        return true;
    }

    /**
     * @param context     The execution context.
     * @param mediaPlayer The media player to setup.
     * @param rawResId    The ID of the raw resource to link to the media player.
     * @return <code>true</code> when call to {@link MediaPlayer#setDataSource(FileDescriptor, long, long)} is ok.
     */
    public static boolean setupSoundSource(@NonNull final Context context,
                                           @NonNull final MediaPlayer mediaPlayer,
                                           @RawRes final int rawResId) throws IOException {

        if (rawResId == 0) {
            Tracker.INSTANCE.track(new RuntimeException("Bundled sound identified but no raw resource ID provided. Value can't be 0."));
            return false;
        }

        try (AssetFileDescriptor fileDescriptor = context.getResources().openRawResourceFd(rawResId)) {
            mediaPlayer.setDataSource(fileDescriptor.getFileDescriptor(), fileDescriptor.getStartOffset(), fileDescriptor.getLength());
            return true;
        }
    }

}

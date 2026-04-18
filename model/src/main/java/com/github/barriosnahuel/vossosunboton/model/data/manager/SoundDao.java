package com.github.barriosnahuel.vossosunboton.model.data.manager;

import android.content.Context;

import androidx.annotation.NonNull;

import com.github.barriosnahuel.vossosunboton.commons.file.FileUtils;
import com.github.barriosnahuel.vossosunboton.model.Sound;
import com.github.barriosnahuel.vossosunboton.model.data.local.Storage;
import com.github.barriosnahuel.vossosunboton.model.data.local.defaultaudios.PackagedAudios;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import timber.log.Timber;

/**
 * Currently it only handles local storage.
 *
 * @author Nahuel Barrios, on 9/4/16.
 */
public class SoundDao {

    private static final String SOUNDS_NAME = "sounds.name";
    private static final String SOUNDS_FILE_NAME_PREFFIX = "sounds.file.";
    private static final String SOUNDS_FAVORITE_PREFFIX = "sounds.favorite.";
    private final Storage storage;

    /**
     * Creates a new instance of this DAO and its dependencies.
     */
    public SoundDao() {
        storage = new Storage();
    }

    /**
     * @param context The execution context.
     * @param sound   The sound to save for this user.
     */
    public void save(final Context context, final Sound sound) {
        final Set<String> names = storage.getAll(context, SOUNDS_NAME);
        names.add(sound.getName());
        storage.save(context, SOUNDS_NAME, names);
        storage.save(context, SOUNDS_FILE_NAME_PREFFIX + sound.getName(), sound.getFile());
    }

    /**
     * @param context The execution context.
     * @return The list of sounds currently stored for this user.
     */
    public List<Sound> find(final Context context) {
        final List<Sound> sounds = new ArrayList<>(0);

        final Set<String> names = storage.getAll(context, SOUNDS_NAME);

        for (final String eachSoundName : names) {
            final String fileName = storage.get(context, SOUNDS_FILE_NAME_PREFFIX + eachSoundName);
            final boolean isFavorite = "true".equals(storage.get(context, SOUNDS_FAVORITE_PREFFIX + eachSoundName));
            sounds.add(new Sound(eachSoundName, fileName, 0, false, isFavorite));
        }

        sounds.addAll(PackagedAudios.get(context));

        return sounds;
    }

    /**
     * @param context    The execution context.
     * @param soundName  Name of the sound to update.
     * @param isFavorite Whether the sound is a favorite.
     */
    public void saveFavorite(final Context context, final String soundName, final boolean isFavorite) {
        storage.save(context, SOUNDS_FAVORITE_PREFFIX + soundName, String.valueOf(isFavorite));
    }

    /**
     * @param context The execution context.
     * @param sound   The sound to delete.
     * @return <code>true</code> when the file was successfully deleted. Otherwise <code>false</code>.
     * @throws IllegalStateException when trying to delete a user's custom button without a persisted file in file system.
     */
    public boolean delete(final @NonNull Context context, final @NonNull Sound sound) {
        final String file = sound.getFile();
        Timber.i("Trying to delete button. Button: '%s'", sound.getName());

        if (file == null) {
            throw new IllegalStateException("Requested to delete a user's custom button without a persisted file in file system");
        }

        final boolean deleted = FileUtils.deleteFile(context, file);
        if (deleted) {
            Timber.i("Button file successfully deleted. Button: %s", sound.getName());
            deleteButtonKey(context, sound.getName());
            deleteButtonKeyFileMapping(context, sound.getName());
            storage.remove(context, SOUNDS_FAVORITE_PREFFIX + sound.getName());
        } else {
            Timber.e("Button could NOT be deleted. Button: %s", sound.getName());
        }

        return deleted;
    }

    private void deleteButtonKey(final @NonNull Context context, final @NonNull String name) {
        final Set<String> names = storage.getAll(context, SOUNDS_NAME);

        Timber.d("Total sounds before update: %s", names.size());
        names.remove(name);
        storage.save(context, SOUNDS_NAME, names);

        Timber.d("Total sounds after update: %s", storage.getAll(context, SOUNDS_NAME).size());
    }

    private void deleteButtonKeyFileMapping(final @NonNull Context context, final @NonNull String name) {
        final String key = SOUNDS_FILE_NAME_PREFFIX + name;

        storage.remove(context, key);
        Timber.d("Button removed from app mappings: %s", key);
    }
}

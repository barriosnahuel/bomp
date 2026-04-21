package com.github.barriosnahuel.vossosunboton.model.data.manager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.local.Storage
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

internal class SoundDaoTest : AbstractRobolectricTest() {
    private lateinit var context: Context
    private lateinit var dao: SoundDao
    private lateinit var storage: Storage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences("my-prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        dao = SoundDao()
        storage = Storage()
    }

    @Test
    fun `save then find returns the saved custom sound`() {
        dao.save(context, Sound("bell", "bell.mp3"))

        val result = dao.find(context).filter { !it.isBundled() }

        assertThat(result).hasSize(1)
        assertThat(result.single().name).isEqualTo("bell")
        assertThat(result.single().file).isEqualTo("bell.mp3")
    }

    @Test
    fun `save then clear prefs then find returns no custom sounds`() {
        dao.save(context, Sound("bell", "bell.mp3"))

        context
            .getSharedPreferences("my-prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        assertThat(dao.find(context).filter { !it.isBundled() }).isEmpty()
    }

    @Test
    fun `after simulated restore find returns the originally saved sound`() {
        dao.save(context, Sound("bell", "bell.mp3"))

        // Capture what was persisted so we can re-inject it (simulating OS restore)
        val names = storage.getAll(context, "sounds.name").toSet()
        val file = storage.get(context, "sounds.file.bell")

        // Simulate OS wiping the data dir before restore
        context
            .getSharedPreferences("my-prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        // Simulate OS writing the backed-up prefs back
        storage.save(context, "sounds.name", names)
        storage.save(context, "sounds.file.bell", file)

        val result = dao.find(context).filter { !it.isBundled() }

        assertThat(result).hasSize(1)
        assertThat(result.single().name).isEqualTo("bell")
        assertThat(result.single().file).isEqualTo("bell.mp3")
    }

    @Test
    fun `saveFavorite is preserved across a simulated restore`() {
        dao.save(context, Sound("bell", "bell.mp3"))
        dao.saveFavorite(context, "bell", true)

        val names = storage.getAll(context, "sounds.name").toSet()
        val file = storage.get(context, "sounds.file.bell")
        val favorite = storage.get(context, "sounds.favorite.bell")

        context
            .getSharedPreferences("my-prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        storage.save(context, "sounds.name", names)
        storage.save(context, "sounds.file.bell", file)
        storage.save(context, "sounds.favorite.bell", favorite)

        val result = dao.find(context).filter { !it.isBundled() }

        assertThat(result.single().isFavorite).isTrue()
    }

    @Test
    fun `savePin true is returned as isPinned by find`() {
        dao.save(context, Sound("bell", "bell.mp3"))

        dao.savePin(context, "bell", true)

        assertThat(dao.find(context).single { it.name == "bell" }.isPinned).isTrue()
    }

    @Test
    fun `savePin false overrides a previous savePin true`() {
        dao.save(context, Sound("bell", "bell.mp3"))
        dao.savePin(context, "bell", true)

        dao.savePin(context, "bell", false)

        assertThat(dao.find(context).single { it.name == "bell" }.isPinned).isFalse()
    }

    @Test
    fun `savePin is preserved across a simulated restore`() {
        dao.save(context, Sound("bell", "bell.mp3"))
        dao.savePin(context, "bell", true)

        val names = storage.getAll(context, "sounds.name").toSet()
        val file = storage.get(context, "sounds.file.bell")
        val pinned = storage.get(context, "sounds.pinned.bell")

        context
            .getSharedPreferences("my-prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        storage.save(context, "sounds.name", names)
        storage.save(context, "sounds.file.bell", file)
        storage.save(context, "sounds.pinned.bell", pinned)

        val result = dao.find(context).filter { !it.isBundled() }

        assertThat(result.single().isPinned).isTrue()
    }
}

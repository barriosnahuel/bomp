package com.github.barriosnahuel.vossosunboton.feature.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.model.R
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import org.junit.Test
import java.io.File

internal class ShareFeatureTest : AbstractRobolectricTest() {
    private val dummyButtonName = "my button name"

    @Test
    fun `share when sound Uri and resource are null must throw an exception`() {
        val sound = givenASoundWithNullUri()

        try {
            whenSharingThe(sound)
        } catch (e: IllegalStateException) {
            thenWeThrowAnIllegalStateException(e)
        }
    }

    @Test
    fun `share when sound URI is valid show disambiguation window`() {
        val sound = givenASoundWithUri()

        val generatedIntent = whenSharingThe(sound)

        thenOSShowDisambiguationWindow(generatedIntent)
    }

    @Test
    fun `share when sound raw resource ID is valid show disambiguation window`() {
        val sound = givenASoundWithResourceId()

        val generatedIntent = whenSharingThe(sound)

        thenOSShowDisambiguationWindow(generatedIntent)
    }

    @Test
    fun `share when sound URI is valid sends the audio file`() {
        val sound = givenASoundWithUri()

        val generatedIntent = whenSharingThe(sound)

        thenWeSendAnAudio(generatedIntent)
    }

    @Test
    fun `share should generate a content Uri under Music directory as described in app_file_provider_paths resource`() {
        val sound = givenASoundWithUri()

        val capturedFile = whenSharingTheSoundCapturingThePath(sound)

        thenWeCheckSoundPathIsAtMusicDirectory(capturedFile)
    }

    private fun thenWeCheckSoundPathIsAtMusicDirectory(capturedFile: File) {
        assertThat(capturedFile.absolutePath.split("/").contains(Environment.DIRECTORY_MUSIC)).isTrue()
    }

    private fun givenASoundWithUri() = Sound(dummyButtonName, "a/dummy/sound/uri")

    private fun givenASoundWithResourceId() = Sound(dummyButtonName, rawRes = R.raw.model_sample_button_activar)

    private fun givenASoundWithNullUri() = Sound(dummyButtonName)

    private fun whenSharingTheSoundCapturingThePath(sound: Sound): File {
        val mockedContext = spyk<Context>(ApplicationProvider.getApplicationContext<Context>())

        val slotFile = slot<File>()
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(mockedContext, any(), capture(slotFile)) } returns Uri.EMPTY

        val slot = slot<Intent>()
        every { mockedContext.startActivity(capture(slot)) } answers { nothing }

        ShareFeature.instance.share(mockedContext, sound)

        return slotFile.captured
    }

    private fun whenSharingThe(sound: Sound): Intent {
        val mockedContext = spyk<Context>(ApplicationProvider.getApplicationContext<Context>())

        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(mockedContext, any(), any()) } returns Uri.EMPTY

        val slot = slot<Intent>()
        every { mockedContext.startActivity(capture(slot)) } answers { nothing }

        ShareFeature.instance.share(mockedContext, sound)

        return slot.captured
    }

    private fun thenWeThrowAnIllegalStateException(e: IllegalStateException) {
        assertThat(e.message).isEqualTo("Either file URI or raw resource ID must exist on a given sound")
    }

    private fun thenOSShowDisambiguationWindow(intent: Intent) {
        assertThat(intent.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(intent.extras?.containsKey(Intent.EXTRA_INTENT)).isTrue()
    }

    private fun thenWeSendAnAudio(intent: Intent) {
        val shareIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)

        assertThat(shareIntent?.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(shareIntent?.type).isEqualTo("audio/*")
        assertThat(shareIntent?.extras?.containsKey(Intent.EXTRA_STREAM)).isTrue()
    }
}

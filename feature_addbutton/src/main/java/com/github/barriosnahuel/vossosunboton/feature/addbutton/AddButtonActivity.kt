package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme

class AddButtonActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        val editSoundName = intent.getStringExtra(LandingActivity.EXTRA_EDIT_SOUND_NAME)
        if (editSoundName != null) {
            launchEditAddButtonMode(editSoundName)
            return
        }

        val uri: Uri? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        if (uri == null) {
            Toast.makeText(this, R.string.feature_addbutton_missing_parameter_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        launchCreateAddButtonMode(uri)
    }

    private fun launchEditAddButtonMode(soundName: String) {
        val file = intent.getStringExtra(LandingActivity.EXTRA_EDIT_SOUND_FILE)
        val isFavorite = intent.getBooleanExtra(LandingActivity.EXTRA_EDIT_SOUND_FAVORITE, false)
        val dateAddedRaw = intent.getLongExtra(LandingActivity.EXTRA_EDIT_SOUND_DATE_ADDED, 0L)
        val sound = Sound(soundName, file, 0, false, isFavorite, if (dateAddedRaw > 0L) dateAddedRaw else null)
        setContent {
            AppTheme {
                AddButtonScreen(
                    context = this,
                    mode = AddButtonMode.Edit(sound),
                    onSaved = { name -> navigateBackRenamed(name) },
                    onNavigateUp = { finish() },
                )
            }
        }
    }

    private fun launchCreateAddButtonMode(uri: Uri) {
        setContent {
            AppTheme {
                AddButtonScreen(
                    context = this,
                    mode = AddButtonMode.Create(uri),
                    onSaved = { name -> navigateBackSaved(name) },
                    onNavigateUp = { finish() },
                )
            }
        }
    }

    private fun navigateBackSaved(name: String) {
        startActivity(
            Intent(this, LandingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(LandingActivity.EXTRA_BUTTON_SAVED, true)
                putExtra(LandingActivity.EXTRA_BUTTON_NAME, name)
            },
        )
        finishAndRemoveTask()
    }

    private fun navigateBackRenamed(name: String) {
        startActivity(
            Intent(this, LandingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(LandingActivity.EXTRA_BUTTON_RENAMED, true)
                putExtra(LandingActivity.EXTRA_BUTTON_NAME, name)
            },
        )
        finishAndRemoveTask()
    }
}

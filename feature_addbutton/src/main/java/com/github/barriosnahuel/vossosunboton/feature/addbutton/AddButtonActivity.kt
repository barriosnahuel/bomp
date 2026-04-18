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
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme

class AddButtonActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

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

        setContent {
            AppTheme {
                AddButtonScreen(
                    context = this,
                    uri = uri,
                    onSaved = {
                        startActivity(
                            Intent(this, LandingActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra(LandingActivity.EXTRA_BUTTON_SAVED, true)
                            },
                        )
                        finishAndRemoveTask()
                    },
                    onNavigateUp = { finish() },
                )
            }
        }
    }
}

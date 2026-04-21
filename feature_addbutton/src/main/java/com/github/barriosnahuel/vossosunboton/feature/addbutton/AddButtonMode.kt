package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.net.Uri
import com.github.barriosnahuel.vossosunboton.model.Sound

sealed class AddButtonMode {
    data class Create(
        val uri: Uri,
    ) : AddButtonMode()

    data class Edit(
        val sound: Sound,
    ) : AddButtonMode()
}

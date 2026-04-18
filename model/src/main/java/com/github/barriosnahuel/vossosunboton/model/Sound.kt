package com.github.barriosnahuel.vossosunboton.model

import androidx.annotation.RawRes

/**
 * Represents a sound (a.k.a. a button of this app).
 */
data class Sound(
    val name: String,
    val file: String?,
    @field:RawRes @param:RawRes val rawRes: Int,
    val isPlaying: Boolean,
) {
    constructor(name: String, file: String?) : this(name, file, 0, false)
    constructor(
        name: String,
        @RawRes rawRes: Int = 0,
    ) : this(name, null, rawRes, false)

    /**
     * Indicates whether or not this sound is bundled in the app or is a user's custom sound.
     */
    fun isBundled() = file == null
}

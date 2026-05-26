/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton

import java.util.Locale

/**
 * Realistic, locale-aware seed content for debug installs and the store-screenshot capture.
 *
 * Audio and collection display names are user data — not localized resources — so they are chosen
 * here per language to make a clean debug board (and the marketing screenshots) read like a real
 * Bomper's collection instead of the raw bundled-sample names. Shared between [DebugSoundSeeder]
 * (the manual `install-debug-seeded.sh` path) and the instrumented store-screenshot capture test,
 * so both render the same canonical content. Names follow brand-DNA: affective, "los tuyos",
 * voseo for es-AR — no anti-tags.
 */
internal object DebugSeedCorpus {
    /**
     * One seeded audio: a stable, filename-safe [key]; its display [name]; the public and the
     * non-system private collection display names it joins; whether it also lives in the system
     * Baúl ([inSystemVault]); and whether it shows in My Sounds ([visibleInMySounds] = `false` for a
     * Vault-only audio, per ADR 0012).
     */
    internal data class SeedItem(
        val key: String,
        val name: String,
        val publicCollections: List<String> = emptyList(),
        val privateCollections: List<String> = emptyList(),
        val inSystemVault: Boolean = false,
        val visibleInMySounds: Boolean = true,
    )

    /** A canonical seed set for one locale: the collections to ensure + the audios to plant. */
    internal data class Corpus(
        val publicCollections: List<String>,
        val privateCollections: List<String>,
        val items: List<SeedItem>,
    )

    /** es for any Spanish locale, English otherwise (the two locales the store listing ships). */
    fun forLocale(locale: Locale): Corpus = if (locale.language == "es") SPANISH else ENGLISH

    private val SPANISH =
        Corpus(
            // Canonical collection order: Familia → Amigos → Laburo → Los pibes (drives the My Sounds
            // filter chips + the Manage Collections sections).
            publicCollections = listOf("Familia", "Amigos", "Laburo", "Los pibes"),
            privateCollections = listOf("Solo nuestro"),
            // My Sounds and the Vault render newest-first, so this list is seeded oldest→newest — the
            // REVERSE of what shows on screen. It's ordered so My Sounds reads Familia → Amigos →
            // Laburo → Los pibes top-to-bottom, and the Vault leads with "Para los días grises".
            items =
                listOf(
                    SeedItem("partido-ocho", "Partido a las 8", publicCollections = listOf("Los pibes")),
                    SeedItem("frase-jefe", "La frase del jefe", publicCollections = listOf("Laburo")),
                    SeedItem("reunion-lunes", "Reunión del lunes", publicCollections = listOf("Laburo")),
                    SeedItem("asado-listo", "Salió el asado", publicCollections = listOf("Amigos")),
                    SeedItem("joda-finde", "Joda del finde", publicCollections = listOf("Amigos")),
                    SeedItem("risa-vieja", "Risa de mi vieja", publicCollections = listOf("Familia")),
                    SeedItem("llegue-bien", "Llegué bien", publicCollections = listOf("Familia")),
                    SeedItem("mama-llamo", "Mamá llamó", publicCollections = listOf("Familia")),
                    SeedItem("buen-dia-amor", "Buen día, amor", privateCollections = listOf("Solo nuestro")),
                    SeedItem("te-re-quiero", "Te re quiero", inSystemVault = true, visibleInMySounds = false),
                    SeedItem(
                        "dias-grises",
                        "Para los días grises",
                        privateCollections = listOf("Solo nuestro"),
                        visibleInMySounds = false,
                    ),
                ),
        )

    private val ENGLISH =
        Corpus(
            // Canonical collection order: Family → Friends → Work → The crew (mirrors es-AR).
            publicCollections = listOf("Family", "Friends", "Work", "The crew"),
            privateCollections = listOf("Just ours"),
            // Seeded oldest→newest (the reverse of the newest-first on-screen order) so My Sounds
            // reads Family → Friends → Work → The crew top-to-bottom, and the Vault leads with "For
            // the grey days".
            items =
                listOf(
                    SeedItem("game-at-eight", "Game at eight", publicCollections = listOf("The crew")),
                    SeedItem("boss-catchphrase", "The boss's catchphrase", publicCollections = listOf("Work")),
                    SeedItem("monday-standup", "Monday standup", publicCollections = listOf("Work")),
                    SeedItem("bbq-ready", "BBQ's ready", publicCollections = listOf("Friends")),
                    SeedItem("weekend-plans", "Weekend plans", publicCollections = listOf("Friends")),
                    SeedItem("moms-laugh", "Mom's laugh", publicCollections = listOf("Family")),
                    SeedItem("made-it-home", "Made it home", publicCollections = listOf("Family")),
                    SeedItem("mom-called", "Mom called", publicCollections = listOf("Family")),
                    SeedItem("morning-love", "Morning, love", privateCollections = listOf("Just ours")),
                    SeedItem("love-you-lots", "Love you lots", inSystemVault = true, visibleInMySounds = false),
                    SeedItem(
                        "grey-days",
                        "For the grey days",
                        privateCollections = listOf("Just ours"),
                        visibleInMySounds = false,
                    ),
                ),
        )
}

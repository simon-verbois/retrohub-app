package ovh.verbois.retrohub.emulation

import ovh.verbois.retrohub.core.DeviceClass

/**
 * Options libretro par cœur (clés et valeurs relevées dans les sources des cœurs, voir
 * docs/cores.md) : réglages conseillés, ajustés au profil de performance et à l’appareil,
 * puis les choix du joueur, puis ses options avancées.
 */
object CoreOptions {
    fun build(spec: ConsoleSpec, core: String, settings: AppSettings, device: DeviceClass, portrait: Boolean): Map<String, String> {
        val console = settings.console(spec.id)
        val scale = resolution(spec, settings, device)
        val options =
            when (core) {
                "fceumm" -> mapOf(
                    "fceumm_aspect" to "4:3",
                    "fceumm_sndquality" to "High",
                    "fceumm_overscan" to "enabled",
                    "fceumm_palette" to "default",
                )

                "snes9x" -> mapOf("snes9x_aspect" to "4:3", "snes9x_overscan" to "auto", "snes9x_audio_interpolation" to "gaussian")

                "gambatte" -> mapOf(
                    "gambatte_gb_colorization" to "auto",
                    "gambatte_gbc_color_correction" to "GBC only",
                    "gambatte_mix_frames" to if (settings.performance == PerformanceProfile.BATTERY) "disabled" else "lcd_ghosting_fast",
                )

                "mgba" ->
                    mapOf(
                        "mgba_color_correction" to "Auto",
                        "mgba_interframe_blending" to if (settings.performance == PerformanceProfile.BATTERY) "OFF" else "mix_smart",
                        "mgba_skip_bios" to "ON",
                        "mgba_frameskip" to if (device == DeviceClass.MODEST) "auto" else "disabled",
                        "mgba_idle_optimization" to "Remove Known",
                    )

                "genesis_plus_gx" ->
                    mapOf(
                        "genesis_plus_gx_region_detect" to "auto",
                        "genesis_plus_gx_aspect_ratio" to "auto",
                        "genesis_plus_gx_ym2612" to if (device == DeviceClass.MODEST) "mame (ym2612)" else "nuked (ym3438)",
                        "genesis_plus_gx_frameskip" to if (device == DeviceClass.MODEST) "auto" else "disabled",
                        "genesis_plus_gx_no_sprite_limit" to "disabled",
                    )

                "pcsx_rearmed" ->
                    mapOf(
                        "pcsx_rearmed_neon_enhancement_enable" to if (scale >= 2) "enabled" else "disabled",
                        "pcsx_rearmed_neon_enhancement_tex_adj_v2" to "enabled",
                        "pcsx_rearmed_spu_interpolation" to "gaussian",
                        "pcsx_rearmed_frameskip_type" to if (device == DeviceClass.MODEST) "auto" else "disabled",
                        "pcsx_rearmed_gpu_thread_rendering" to "auto",
                        "pcsx_rearmed_drc" to "enabled",
                        "pcsx_rearmed_memcard1" to "libretro",
                        "pcsx_rearmed_vibration" to "enabled",
                    )

                // Le rendu OpenGL de SwanStation réclame un contexte desktop que LibretroDroid n’a pas.
                "swanstation" ->
                    mapOf(
                        "swanstation_GPU_Renderer" to "Software",
                        "swanstation_CPU_ExecutionMode" to "Recompiler",
                        "swanstation_Display_AspectRatio" to if (console.widescreen == true) "16:9" else "4:3",
                        "swanstation_GPU_WidescreenHack" to if (console.widescreen == true) "true" else "false",
                        "swanstation_MemoryCards_Card1Type" to "Libretro",
                        "swanstation_BIOS_PatchFastBoot" to "false",
                    )

                "melonds" ->
                    mapOf(
                        "melonds_screen_layout" to dsLayoutValue(settings, portrait),
                        "melonds_screen_gap" to "0",
                        "melonds_hybrid_small_screen" to if (console.dsSwapScreens == true) "Top" else "Bottom",
                        "melonds_touch_mode" to "Touch",
                        "melonds_language" to "French",
                        "melonds_boot_directly" to "enabled",
                        "melonds_jit_enable" to "enabled",
                        "melonds_threaded_renderer" to if (device == DeviceClass.MODEST) "disabled" else "enabled",
                        "melonds_audio_interpolation" to if (settings.performance == PerformanceProfile.BATTERY) "None" else "Cubic",
                    )

                "azahar" -> mapOf(
                    "citra_graphics_api" to "OpenGL",
                    "citra_resolution_factor" to scale.toString(),
                    "citra_layout_option" to threeDsLayoutValue(
                        (if (portrait) console.dsLayoutPortrait else console.dsLayoutLandscape) ?: GameLayouts.defaultDsLayout(portrait),
                    ),
                    "citra_swap_screen" to if (console.dsSwapScreens == true) "Bottom" else "Top",
                    "citra_use_libretro_save_path" to "LibRetro Default",
                )

                "armsx2" -> mapOf(
                    "armsx2_renderer" to "OpenGL",
                    "armsx2_upscale" to "${scale}x",
                    "armsx2_fast_boot" to "enabled",
                )

                "stella2014" -> mapOf("stella2014_palette" to "standard", "stella2014_mix_frames" to "disabled")

                // Glide64 tourne en GLES2 ; le rendu ParaLLEl par défaut exige Vulkan.
                "parallel_n64" ->
                    mapOf(
                        "parallel-n64-gfxplugin" to "glide64",
                        "parallel-n64-rspplugin" to "hle",
                        "parallel-n64-enhanced-hle-audio" to "enabled",
                        "parallel-n64-enhanced-hle-audio-quality" to "32",
                        "parallel-n64-cpucore" to "dynamic_recompiler",
                        "parallel-n64-screensize" to n64ScreenSize(scale),
                        "parallel-n64-aspectratiohint" to if (console.widescreen == true) "widescreen" else "normal",
                        "parallel-n64-gfxplugin-accuracy" to if (device == DeviceClass.MODEST) "low" else "medium",
                        "parallel-n64-filtering" to "automatic",
                        "parallel-n64-pak1" to "memory",
                        "parallel-n64-disable_expmem" to "enabled",
                        "parallel-n64-astick-deadzone" to "15",
                    )

                "dolphin" ->
                    mapOf(
                        "dolphin_renderer" to "Hardware",
                        "dolphin_efb_scale" to scale.toString(),
                        "dolphin_cpu_core" to "4",
                        "dolphin_main_cpu_thread" to "enabled",
                        "dolphin_fastmem" to "enabled",
                        "dolphin_shader_compilation_mode" to "2",
                        "dolphin_aspect_ratio" to if (console.widescreen == true) "1" else "0",
                        "dolphin_widescreen_hack" to if (console.widescreen == true) "enabled" else "disabled",
                        "dolphin_language" to "3",
                        "dolphin_skip_gc_bios" to "enabled",
                        "dolphin_dsp_hle" to "enabled",
                        "dolphin_skip_dupe_frames" to "enabled",
                        "dolphin_pal60" to "enabled",
                    )

                "flycast" ->
                    mapOf(
                        "reicast_internal_resolution" to "${scale}x",
                        "reicast_widescreen_hack" to if (console.widescreen == true) "enabled" else "disabled",
                        "reicast_language" to "French",
                        "reicast_threaded_rendering" to "enabled",
                        "reicast_frame_skipping" to if (device == DeviceClass.MODEST) "1" else "disabled",
                        "reicast_alpha_sorting" to
                            if (device == DeviceClass.MODEST) "per-strip (fast, least accurate)" else "per-triangle (normal)",
                        "reicast_anisotropic_filtering" to if (device == DeviceClass.MODEST) "disabled" else "4",
                        "reicast_enable_purupuru" to "enabled",
                        "reicast_per_content_vmus" to "VMU A1",
                    )

                else -> emptyMap()
            }
        return options + overrides(spec, core, settings) + (if (core == (console.core ?: spec.cores.first())) console.options else emptyMap()) +
            console.optionsByCore[core].orEmpty() + when (core) {
                "azahar" -> mapOf("citra_use_libretro_save_path" to "LibRetro Default", "citra_graphics_api" to "OpenGL")
                "armsx2" -> mapOf("armsx2_renderer" to "OpenGL")
                else -> emptyMap()
            }
    }

    /** Choix du joueur traduits en options de chaque cœur : langue, région, format, démarrage, saut d’images. */
    private fun overrides(spec: ConsoleSpec, core: String, settings: AppSettings): Map<String, String> {
        val console = settings.console(spec.id)
        val out = mutableMapOf<String, String>()
        val french = settings.language(spec.id) == GameLanguage.FRENCH
        when (core) {
            "melonds" -> out["melonds_language"] = if (french) "French" else "English"
            "dolphin" -> out["dolphin_language"] = if (french) "3" else "1"
            "flycast" -> out["reicast_language"] = if (french) "French" else "English"
        }
        console.region?.let { region ->
            val value =
                when (core) {
                    "fceumm" -> mapOf(Region.AUTO to "Auto", Region.EUROPE to "PAL", Region.USA to "NTSC", Region.JAPAN to "NTSC")
                    "snes9x" -> mapOf(Region.AUTO to "auto", Region.EUROPE to "pal", Region.USA to "ntsc", Region.JAPAN to "ntsc")
                    "genesis_plus_gx" -> mapOf(Region.AUTO to "auto", Region.EUROPE to "pal", Region.USA to "ntsc-u", Region.JAPAN to "ntsc-j")
                    "pcsx_rearmed" -> mapOf(Region.AUTO to "auto", Region.EUROPE to "PAL", Region.USA to "NTSC", Region.JAPAN to "NTSC")
                    "swanstation" -> mapOf(Region.AUTO to "Auto", Region.EUROPE to "PAL", Region.USA to "NTSC-U", Region.JAPAN to "NTSC-J")
                    "flycast" -> mapOf(Region.AUTO to "Default", Region.EUROPE to "Europe", Region.USA to "USA", Region.JAPAN to "Japan")
                    else -> emptyMap()
                }[region]
            val key = REGION_KEYS[core]
            if (value != null && key != null) out[key] = value
        }
        val aspect = if (console.integerScale == true) AspectChoice.PIXELS else console.aspect
        aspect?.let {
            when (core) {
                "fceumm" -> out["fceumm_aspect"] = if (it == AspectChoice.TV) "4:3" else "8:7 PAR"
                "snes9x" -> out["snes9x_aspect"] = if (it == AspectChoice.TV) "4:3" else "uncorrected"
                "genesis_plus_gx" -> out["genesis_plus_gx_aspect_ratio"] = if (it == AspectChoice.TV) "4:3" else "Uncorrected"
                "swanstation" -> out["swanstation_Display_AspectRatio"] = if (it == AspectChoice.TV) "4:3" else "PAR 1:1"
            }
        }
        val boot = console.boot ?: defaultBoot(spec.id)
        val fast = boot == BootMode.FAST
        when (core) {
            "swanstation" -> out["swanstation_BIOS_PatchFastBoot"] = if (fast) "true" else "false"
            "melonds" -> out["melonds_boot_directly"] = if (fast) "enabled" else "disabled"
            "mgba" -> out["mgba_skip_bios"] = if (fast) "ON" else "OFF"
            "dolphin" -> out["dolphin_skip_gc_bios"] = if (fast) "enabled" else "disabled"
            "flycast" -> out["reicast_use_real_bios"] = if (fast) "disabled" else "enabled"
            "genesis_plus_gx" -> out["genesis_plus_gx_bios"] = if (fast) "disabled" else "enabled"
        }
        when (console.frameSkip) {
            FrameSkip.OFF ->
                FRAMESKIP_KEYS[core]?.let { (key, off, _) -> out[key] = off }

            FrameSkip.ON ->
                FRAMESKIP_KEYS[core]?.let { (key, _, on) -> out[key] = on }

            else -> {}
        }
        return out
    }

    private val REGION_KEYS =
        mapOf(
            "fceumm" to "fceumm_region",
            "snes9x" to "snes9x_region",
            "genesis_plus_gx" to "genesis_plus_gx_region_detect",
            "pcsx_rearmed" to "pcsx_rearmed_region",
            "swanstation" to "swanstation_Console_Region",
            "flycast" to "reicast_region",
        )

    private val FRAMESKIP_KEYS =
        mapOf(
            "mgba" to Triple("mgba_frameskip", "disabled", "auto"),
            "genesis_plus_gx" to Triple("genesis_plus_gx_frameskip", "disabled", "auto"),
            "pcsx_rearmed" to Triple("pcsx_rearmed_frameskip_type", "disabled", "auto"),
            "flycast" to Triple("reicast_frame_skipping", "disabled", "1"),
        )

    fun defaultBoot(console: String): BootMode = if (console == "dreamcast") BootMode.ORIGINAL else BootMode.FAST

    fun supportsLanguage(core: String) = core in setOf("melonds", "dolphin", "flycast")

    fun supportsRegion(core: String) = core in REGION_KEYS

    fun supportsAspect(core: String) = core in setOf("fceumm", "snes9x", "genesis_plus_gx", "swanstation")

    fun supportsBoot(core: String) = core in setOf("swanstation", "melonds", "mgba", "dolphin", "flycast", "genesis_plus_gx")

    fun supportsFrameSkip(core: String) = core in FRAMESKIP_KEYS

    fun supportsAnalog(console: String) = console == "psx"

    /** Résolution interne des consoles 3D : choix du joueur, sinon selon le profil et l’appareil. */
    fun resolution(spec: ConsoleSpec, settings: AppSettings, device: DeviceClass): Int {
        settings.console(spec.id).resolution?.let { return it.coerceIn(1, maxResolution(spec.id)) }
        return when (settings.performance) {
            PerformanceProfile.BATTERY -> 1

            PerformanceProfile.BALANCED -> if (device == DeviceClass.POWERFUL && spec.id !in setOf("gamecube", "3ds", "ps2")) 2 else 1

            PerformanceProfile.QUALITY -> if (device == DeviceClass.MODEST) {
                1
            } else if (spec.id == "psx") {
                2
            } else {
                3
            }
        }.coerceIn(1, maxResolution(spec.id))
    }

    /** Multiplicateurs proposés au joueur pour chaque console 3D (1 seul = pas de réglage). */
    fun maxResolution(console: String): Int = when (console) {
        "psx" -> 2
        "n64" -> 4
        "gamecube" -> 4
        "dreamcast", "3ds", "ps2" -> 4
        else -> 1
    }

    fun supportsWidescreen(console: String, core: String): Boolean = core in setOf("swanstation", "parallel_n64", "dolphin", "flycast") && console != "nds"

    fun shader(spec: ConsoleSpec, settings: AppSettings): Shader = settings.console(spec.id).shader ?: when (spec.id) {
        "n64", "gamecube", "dreamcast", "psx", "3ds", "ps2" -> Shader.SMOOTH
        "gb", "gbc", "gba" -> Shader.LCD
        "nds" -> Shader.SHARP
        else -> if (settings.performance == PerformanceProfile.QUALITY) Shader.UPSCALE else Shader.SHARP
    }

    /** melonDS dessine lui-même la disposition des deux écrans. */
    fun dsLayoutValue(settings: AppSettings, portrait: Boolean): String {
        val console = settings.console("nds")
        val layout = (if (portrait) console.dsLayoutPortrait else console.dsLayoutLandscape) ?: GameLayouts.defaultDsLayout(portrait)
        return dsLayoutValue(layout, console.dsSwapScreens == true)
    }

    fun dsLayoutValue(layout: DsLayout, swap: Boolean): String = when (layout) {
        DsLayout.STACKED -> if (swap) "Bottom/Top" else "Top/Bottom"
        DsLayout.SIDE_BY_SIDE -> if (swap) "Right/Left" else "Left/Right"
        DsLayout.MAIN_LARGE -> if (swap) "Hybrid Bottom" else "Hybrid Top"
        DsLayout.TOP_ONLY -> if (swap) "Bottom Only" else "Top Only"
    }

    fun threeDsLayoutValue(layout: DsLayout): String = when (layout) {
        DsLayout.STACKED -> "default"
        DsLayout.SIDE_BY_SIDE -> "side_by_side"
        DsLayout.MAIN_LARGE -> "large_screen"
        DsLayout.TOP_ONLY -> "single_screen"
    }

    private fun n64ScreenSize(scale: Int): String = when (scale) {
        1 -> "640x480"
        2 -> "960x720"
        3 -> "1280x960"
        else -> "1920x1440"
    }
}

package com.jing.sakura.player.mpv

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class Anime4KManager(private val context: Context) {

    enum class Quality(val suffix: String) {
        FAST("S"),
        BALANCED("M"),
        HIGH("L")
    }

    enum class Mode {
        OFF,
        A,
        B,
        C,
        A_PLUS,
        B_PLUS,
        C_PLUS
    }

    private val shaderDir by lazy {
        File(context.filesDir, SHADER_DIR).apply { mkdirs() }
    }

    private var initialized = false

    fun initialize(): Boolean {
        if (initialized) return true
        return try {
            val assets = context.assets.list(SHADER_DIR).orEmpty()
            assets.filter { it.endsWith(".glsl") }.forEach { fileName ->
                val target = File(shaderDir, fileName)
                if (!target.exists()) {
                    context.assets.open("$SHADER_DIR/$fileName").use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            initialized = true
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getShaderChain(mode: Mode, quality: Quality): String {
        if (!initialize() || mode == Mode.OFF) return ""
        val q = quality.suffix
        val shaders = buildList {
            add(shader("Anime4K_Clamp_Highlights.glsl"))
            when (mode) {
                Mode.A -> {
                    add(shader("Anime4K_Restore_CNN_$q.glsl"))
                    add(shader("Anime4K_Upscale_CNN_x2_$q.glsl"))
                    add(shader("Anime4K_AutoDownscalePre_x2.glsl"))
                    add(shader("Anime4K_Upscale_CNN_x2_$q.glsl"))
                }

                Mode.B -> {
                    add(shader("Anime4K_Restore_CNN_Soft_$q.glsl"))
                    add(shader("Anime4K_Upscale_CNN_x2_$q.glsl"))
                    add(shader("Anime4K_AutoDownscalePre_x2.glsl"))
                    add(shader("Anime4K_Upscale_CNN_x2_$q.glsl"))
                }

                Mode.C -> {
                    add(shader("Anime4K_Upscale_Denoise_CNN_x2_$q.glsl"))
                    add(shader("Anime4K_AutoDownscalePre_x2.glsl"))
                    add(shader("Anime4K_Upscale_CNN_x2_$q.glsl"))
                }

                Mode.A_PLUS -> {
                    add(shader("Anime4K_Restore_CNN_$q.glsl"))
                    add(shader("Anime4K_Upscale_CNN_x2_$q.glsl"))
                    add(shader("Anime4K_AutoDownscalePre_x2.glsl"))
                    add(shader("Anime4K_Restore_CNN_$q.glsl"))
                    add(shader("Anime4K_Upscale_CNN_x2_$q.glsl"))
                }

                Mode.B_PLUS -> {
                    add(shader("Anime4K_Restore_CNN_Soft_$q.glsl"))
                    add(shader("Anime4K_Upscale_CNN_x2_$q.glsl"))
                    add(shader("Anime4K_AutoDownscalePre_x2.glsl"))
                    add(shader("Anime4K_Restore_CNN_Soft_$q.glsl"))
                    add(shader("Anime4K_Upscale_CNN_x2_$q.glsl"))
                }

                Mode.C_PLUS -> {
                    add(shader("Anime4K_Upscale_Denoise_CNN_x2_$q.glsl"))
                    add(shader("Anime4K_AutoDownscalePre_x2.glsl"))
                    add(shader("Anime4K_Restore_CNN_$q.glsl"))
                    add(shader("Anime4K_Upscale_CNN_x2_$q.glsl"))
                }

                Mode.OFF -> Unit
            }
        }
        return shaders.joinToString(":")
    }

    private fun shader(name: String): String = File(shaderDir, name).absolutePath

    private companion object {
        private const val SHADER_DIR = "shaders"
    }
}

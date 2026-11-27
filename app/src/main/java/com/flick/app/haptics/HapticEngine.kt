package com.flick.app.haptics

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Haptic tokens from the plan: Tick, Click, LowTick, Thud, Rise.
 * Uses VibrationEffect.Composition primitives when the device supports them,
 * falls back to predefined effects otherwise.
 */
class HapticEngine(context: Context) {
    @Volatile
    var enabled: Boolean = true

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val primitives: Boolean = vibrator?.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_CLICK,
        VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
        VibrationEffect.Composition.PRIMITIVE_THUD,
        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
    ) == true

    private fun play(primitive: Int, scale: Float, fallback: Int) {
        val v = vibrator ?: return
        if (!enabled || !v.hasVibrator()) return
        val effect = if (primitives) {
            VibrationEffect.startComposition().addPrimitive(primitive, scale).compose()
        } else {
            VibrationEffect.createPredefined(fallback)
        }
        v.vibrate(effect)
    }

    fun tick() = play(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f, VibrationEffect.EFFECT_TICK)
    fun click() = play(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, VibrationEffect.EFFECT_CLICK)
    fun lowTick() = play(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.7f, VibrationEffect.EFFECT_TICK)
    fun thud() = play(VibrationEffect.Composition.PRIMITIVE_THUD, 0.9f, VibrationEffect.EFFECT_HEAVY_CLICK)
    fun rise() = play(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.8f, VibrationEffect.EFFECT_CLICK)
}

val LocalHaptics = staticCompositionLocalOf<HapticEngine> { error("HapticEngine not provided") }

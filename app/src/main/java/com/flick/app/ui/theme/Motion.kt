package com.flick.app.ui.theme

import androidx.compose.animation.core.spring

/** Motion tokens. Springs everywhere, nothing linear. */
object Motion {
    /** Bouncy and playful: pin, check, FAB. */
    fun <T> pop() = spring<T>(dampingRatio = 0.55f, stiffness = 500f)

    /** Smooth, no overshoot: layout changes, reorder, snapping. */
    fun <T> settle() = spring<T>(dampingRatio = 0.85f, stiffness = 380f)

    /** Slow and soft: shared element expand. */
    fun <T> expand() = spring<T>(dampingRatio = 0.8f, stiffness = 260f)

    const val StaggerMs = 35L
}

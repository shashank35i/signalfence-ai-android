package com.signalfence.app

import android.graphics.drawable.GradientDrawable

object DotDrawable {
    fun active(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 1000f
            setColor(0xFF0F766E.toInt()) // Primary
        }
    }

    fun inactive(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFD2D8E1.toInt()) // Midnight/Slate 800
        }
    }
}

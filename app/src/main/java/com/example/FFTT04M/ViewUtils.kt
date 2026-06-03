package com.example.FFTT04M

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.google.android.material.slider.Slider

/**
 * Make text scale to fit its view across device sizes and orientations. Walks the tree and enables
 * uniform autosizing on every TextView/Button, so captions stay legible without manual per-device sp.
 *
 * The slider *value* labels (the small TextViews that share a FrameLayout with a Slider) are skipped:
 * they're positioned and sized at runtime by the activities' label code, which would fight autosizing.
 */
fun applyAutoSizeText(root: View, minSp: Int = 6, maxSp: Int = 18) {
    if (root is ViewGroup) {
        val hasSlider = (0 until root.childCount).any { root.getChildAt(it) is Slider }
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            // Inside a slider's FrameLayout, leave the positioned value label alone.
            if (hasSlider && child is TextView && child !is Button) continue
            applyAutoSizeText(child, minSp, maxSp)
        }
    } else if (root is TextView) { // Button is a TextView too
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            root, minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP
        )
    }
}

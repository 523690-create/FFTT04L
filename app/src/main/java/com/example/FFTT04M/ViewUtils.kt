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
    // TabLayout manages its own tab-label sizing; autosizing its internal TextViews fights it.
    if (root is com.google.android.material.tabs.TabLayout) return
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

/**
 * Binary search for the maximum text size that fits within the parent's constraints.
 * Handles multiline text by splitting by '\n' and measuring each line.
 */
fun TextView.setMaxTextSizeToFit(
    text: String,
    maxWidthRatio: Float = 0.9f,
    maxHeightRatio: Float = 0.9f,
    minSizeSp: Float = 6f,
    maxSizeSp: Float = 100f
) {
    val parent = this.parent as? View ?: return
    val targetWidth = parent.width * maxWidthRatio
    val targetHeight = parent.height * maxHeightRatio

    if (targetWidth <= 0 || targetHeight <= 0) return

    val paint = android.graphics.Paint(this.paint)
    var low = minSizeSp
    var high = maxSizeSp
    var best = low

    val lines = text.split("\n")

    while (low <= high) {
        val mid = (low + high) / 2f
        paint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, mid, resources.displayMetrics
        )

        var maxWidth = 0f
        val bounds = android.graphics.Rect()
        val fm = paint.fontMetrics
        val lineHeight = fm.bottom - fm.top

        for (line in lines) {
            paint.getTextBounds(line, 0, line.length, bounds)
            maxWidth = kotlin.math.max(maxWidth, bounds.width().toFloat())
        }
        val totalHeight = lines.size * lineHeight

        if (maxWidth <= targetWidth && totalHeight <= targetHeight) {
            best = mid
            low = mid + 0.1f
        } else {
            high = mid - 0.1f
        }
    }

    setTextSize(TypedValue.COMPLEX_UNIT_SP, best)
    setText(text)
}

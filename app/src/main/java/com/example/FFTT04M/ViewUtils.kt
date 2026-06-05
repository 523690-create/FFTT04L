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
 * EQ/Filter band labels are also skipped; they're handled by updateAllLabelPositions().
 */
fun applyAutoSizeText(root: View, minSp: Int = 6, maxSp: Int = 18) {
    // TabLayout manages its own tab-label sizing; autosizing its internal TextViews fights it.
    if (root is com.google.android.material.tabs.TabLayout) return

    // IDs of labels that are handled separately by updateAllLabelPositions() in ViewerActivity
    val skipIds = intArrayOf(
        R.id.lblEq100, R.id.lblEq300, R.id.lblEq1k, R.id.lblEq3k, R.id.lblEq8k,
        R.id.lblFilterPercent, R.id.lblFilterRise, R.id.lblFilterFall
    )

    if (root is ViewGroup) {
        val hasSlider = (0 until root.childCount).any { root.getChildAt(it) is Slider }
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            // Inside a slider's FrameLayout, leave the positioned value label alone.
            if (hasSlider && child is TextView && child !is Button) continue
            // Skip EQ/Filter band labels that are manually sized elsewhere
            if (child is TextView && child.id in skipIds) continue
            applyAutoSizeText(child, minSp, maxSp)
        }
    } else if (root is TextView) { // Button is a TextView too
        // Skip the specific label IDs
        if (root.id !in skipIds) {
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                root, minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP
            )
        }
    }
}

/**
 * Binary search for the maximum text size that fits within the parent's constraints.
 * Handles multiline text by splitting by '\n' and measuring each line.
 */
/**
 * Binary search for the maximum text size that fits within the parent's constraints.
 * Handles multiline text by splitting by '\n' and measuring each line.
 * Optimized with caching to prevent UI freezes.
 */
fun TextView.setMaxTextSizeToFit(
    text: String,
    maxWidthRatio: Float = 1.0f,
    maxHeightRatio: Float = 1.0f,
    minSizeSp: Float = 6f,
    maxSizeSp: Float = 100f
) {
    val parent = this.parent as? View ?: return

    val runFit = {
        // Size against the view's OWN allocated box when its dimensions are determinate
        // (match_parent or 0dp+weight). Only fall back to the parent for wrap_content
        // dimensions, whose own measured size just echoes the text and can't bound it.
        // This keeps a label/button from ballooning to fill a much larger parent (the cause
        // of clipped EQ labels in narrow landscape columns and truncated top-bar buttons).
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val lpW = this.layoutParams?.width ?: wrap
        val lpH = this.layoutParams?.height ?: wrap
        val w = if (lpW != wrap && this.width > 0) this.width else parent.width
        val h = if (lpH != wrap && this.height > 0) this.height else parent.height

        if (w > 0 && h > 0) {
            val cacheKey = "fit_$text"
            val cacheVal = "${w}_${h}"
            if (this.getTag(R.id.tag_fit_text) != cacheKey || this.getTag(R.id.tag_fit_dims) != cacheVal) {
                // Disable standard autosizing which might fight us
                TextViewCompat.setAutoSizeTextTypeWithDefaults(this, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)

                // Subtract the content padding so text fits the drawable area, not the raw
                // view box. Without this, buttons size text to their full width and the
                // internal padding pushes it into an ellipsis ("GALLERY" -> "GALLE...").
                val padW = (compoundPaddingLeft + compoundPaddingRight).coerceAtLeast(0)
                val padH = (compoundPaddingTop + compoundPaddingBottom).coerceAtLeast(0)
                val targetWidth = (w - padW).coerceAtLeast(1) * maxWidthRatio
                val targetHeight = (h - padH).coerceAtLeast(1) * maxHeightRatio

                val paint = android.graphics.Paint(this.paint)
                var low = minSizeSp
                var high = maxSizeSp
                var best = low

                val lines = text.split("\n")
                // Pin the line count so the text can never spill onto an extra line and wrap
                // per-character (e.g. "Hz" breaking into "H"/"z" in a narrow column).
                this.maxLines = lines.size

                while (low <= high) {
                    val mid = (low + high) / 2f
                    paint.textSize = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, mid, resources.displayMetrics
                    )

                    var maxWidth = 0f
                    val fm = paint.fontMetrics
                    val lineHeight = fm.bottom - fm.top

                    for (line in lines) {
                        // measureText (not getTextBounds) so letterSpacing — applied by Material
                        // buttons — is counted; otherwise width is underestimated and the text
                        // overflows into an ellipsis.
                        maxWidth = kotlin.math.max(maxWidth, paint.measureText(line))
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
                this.setTag(R.id.tag_fit_text, cacheKey)
                this.setTag(R.id.tag_fit_dims, cacheVal)
            }
        }
    }

    if (parent.width <= 0 || parent.height <= 0) {
        parent.post { runFit() }
    } else {
        runFit()
    }
}

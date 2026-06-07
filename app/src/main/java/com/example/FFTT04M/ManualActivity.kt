package com.example.FFTT04M

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat

/**
 * Renders the bundled user manual (assets/user_manual.md) with a lightweight Markdown→HTML
 * converter (headings, bold/italic, bullets, rules, simple tables) shown in a scrollable TextView.
 * Opened from the Gallery's HELP button. Keep user_manual.md updated as features change.
 */
class ManualActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "User Manual"

        val md = try {
            assets.open("user_manual.md").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "# Manual unavailable\n\nCould not load user_manual.md: ${e.message}"
        }

        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val tv = TextView(this).apply {
            text = HtmlCompat.fromHtml(markdownToHtml(md), HtmlCompat.FROM_HTML_MODE_COMPACT)
            setTextColor(0xFFEEEEEE.toInt())
            setLineSpacing(0f, 1.15f)
            setPadding(pad, pad, pad, pad)
            textSize = 15f
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF111111.toInt())
            isFillViewport = true
            addView(tv)
        }
        setContentView(scroll)
    }

    private fun markdownToHtml(md: String): String {
        val sb = StringBuilder()
        for (raw in md.lines()) {
            var line = raw.trimEnd()
            line = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            when {
                line.isBlank() -> sb.append("<br>")
                line.startsWith("### ") -> sb.append("<h3>").append(inline(line.substring(4))).append("</h3>")
                line.startsWith("## ") -> sb.append("<h2>").append(inline(line.substring(3))).append("</h2>")
                line.startsWith("# ") -> sb.append("<h1>").append(inline(line.substring(2))).append("</h1>")
                line.startsWith("---") -> sb.append("<hr>")
                line.startsWith("- ") -> sb.append("&#8226;&nbsp;").append(inline(line.substring(2))).append("<br>")
                line.startsWith("|") -> {
                    val cells = line.trim('|').split("|").map { it.trim() }
                    val isSeparator = cells.all { c -> c.isEmpty() || c.all { ch -> ch == '-' || ch == ':' } }
                    if (!isSeparator) {
                        sb.append("<tt>").append(inline(cells.joinToString("&nbsp;|&nbsp;"))).append("</tt><br>")
                    }
                }
                else -> sb.append(inline(line)).append("<br>")
            }
        }
        return sb.toString()
    }

    /** Inline emphasis: bold (**…**) before italic (*…*), then inline code (`…`). */
    private fun inline(s: String): String {
        var t = s
        t = Regex("""\*\*(.+?)\*\*""").replace(t) { "<b>${it.groupValues[1]}</b>" }
        t = Regex("""\*(.+?)\*""").replace(t) { "<i>${it.groupValues[1]}</i>" }
        t = Regex("""`(.+?)`""").replace(t) { "<tt>${it.groupValues[1]}</tt>" }
        return t
    }
}

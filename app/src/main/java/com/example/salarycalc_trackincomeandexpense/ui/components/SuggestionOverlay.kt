package com.example.salarycalc_trackincomeandexpense.ui.components

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders suggestions as a system-level overlay window that floats ABOVE all
 * app content without taking any layout space. Works on all Android devices
 * including Vivo/MIUI where Compose Popup/DropdownMenu render inline.
 */
@Composable
fun SuggestionOverlay(
    suggestions: List<String>,
    fieldPosition: IntOffset,
    fieldSize: IntSize,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    DisposableEffect(suggestions, fieldPosition) {
        if (suggestions.isEmpty()) return@DisposableEffect onDispose {}

        val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val listView = android.widget.ListView(context).apply {
            adapter = android.widget.ArrayAdapter(
                context,
                android.R.layout.simple_list_item_1,
                suggestions.take(8).toTypedArray()
            )
            setOnItemClickListener { _, _, position, _ ->
                onSelect(suggestions[position])
            }
            divider = null
            dividerHeight = 0
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        val params = WindowManager.LayoutParams(
            fieldSize.width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            token = activity.window.attributes.token
            gravity = Gravity.TOP or Gravity.START
            x = fieldPosition.x
            y = fieldPosition.y + fieldSize.height
        }

        windowManager.addView(listView, params)

        onDispose {
            try { windowManager.removeView(listView) } catch (_: Exception) {}
        }
    }
}

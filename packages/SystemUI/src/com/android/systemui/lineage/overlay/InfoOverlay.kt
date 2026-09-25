/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.lineage.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.PowerManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.android.systemui.res.R
import kotlin.math.roundToInt

/**
 * Small, non-interactive window that periodically displays a piece of information on top of the
 * screen.
 *
 * Subclasses only have to implement [refresh]. It is called on the background thread while the
 * overlay is visible, so implementations may perform slow operations (such as reading sysfs nodes)
 * without blocking the UI.
 */
abstract class InfoOverlay(
    protected val context: Context,
    private val backgroundHandler: Handler,
    private val mainHandler: Handler,
) {
    /** Text that should currently be displayed. Called on the background thread. */
    protected abstract fun refresh(): CharSequence

    /** Delay between two [refresh] calls, in milliseconds. */
    protected open val refreshIntervalMs: Long = 1000L

    /** Gravity used to position the overlay on the screen. */
    protected open val windowGravity: Int = Gravity.TOP or Gravity.START

    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val windowManager = context.getSystemService(WindowManager::class.java)

    @Volatile private var shown = false

    private var viewAdded = false

    private val view: TextView by lazy {
        TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP)
            typeface = Typeface.MONOSPACE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val padding = dp(OVERLAY_PADDING_DP)
            setPadding(padding, padding, padding, padding)
            background =
                GradientDrawable().apply {
                    setColor(OVERLAY_BACKGROUND_COLOR)
                    cornerRadius = dp(OVERLAY_CORNER_RADIUS_DP).toFloat()
                }
        }
    }

    private val ticker =
        object : Runnable {
            override fun run() {
                if (!shown) {
                    return
                }
                // Nothing to update while the screen is off.
                if (powerManager.isInteractive) {
                    val text =
                        try {
                            refresh()
                        } catch (e: RuntimeException) {
                            Log.w(TAG, "Unable to refresh overlay", e)
                            null
                        }
                    if (text != null) {
                        mainHandler.post {
                            if (shown) {
                                view.text = text
                            }
                        }
                    }
                }
                backgroundHandler.postDelayed(this, refreshIntervalMs)
            }
        }

    /** Whether the overlay is currently shown. */
    val isVisible: Boolean
        get() = shown

    /** Shows or hides the overlay. */
    fun setVisible(visible: Boolean) {
        if (shown == visible) {
            return
        }
        shown = visible
        if (visible) {
            mainHandler.post { showWindow() }
            backgroundHandler.post(ticker)
        } else {
            backgroundHandler.removeCallbacks(ticker)
            mainHandler.post { hideWindow() }
        }
    }

    private fun showWindow() {
        if (!shown || viewAdded) {
            return
        }
        try {
            windowManager.addView(view, createLayoutParams())
            viewAdded = true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to show overlay", e)
        }
    }

    private fun hideWindow() {
        if (!viewAdded) {
            return
        }
        viewAdded = false
        try {
            windowManager.removeViewImmediate(view)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to hide overlay", e)
        }
    }

    private fun createLayoutParams() =
        WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            )
            .apply {
                gravity = windowGravity
                x = dp(OVERLAY_MARGIN_DP)
                y =
                    context.resources.getDimensionPixelSize(R.dimen.status_bar_height) +
                        dp(OVERLAY_MARGIN_DP)
                setTitle(TAG)
            }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val TAG = "LineageInfoOverlay"
        const val TEXT_SIZE_SP = 10f
        const val OVERLAY_PADDING_DP = 6
        const val OVERLAY_CORNER_RADIUS_DP = 6
        const val OVERLAY_MARGIN_DP = 8
        val OVERLAY_BACKGROUND_COLOR: Int = 0xB3000000.toInt()
    }
}

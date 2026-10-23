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

package com.android.systemui.biometrics.udfpsanim

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper

/**
 * Plays a UDFPS unlock animation: the frames loop while the finger is down and are cleared once
 * it lifts. All callbacks are marshalled onto the main thread.
 */
class UdfpsAnimationDrawable(
    private val frames: List<Drawable>,
    private val frameDuration: Int,
) : Drawable() {

    private val handler = Handler(Looper.getMainLooper())
    private var index: Int = 0
    private var running: Boolean = false
    private var current: Drawable? = null
    private var alphaValue: Int = 255

    private val advance = object : Runnable {
        override fun run() {
            val list = frames
            if (!running || list.isEmpty()) return
            if (index >= list.size) index = 0
            current = list[index]
            invalidateSelf()
            index++
            handler.postDelayed(this, frameDuration.toLong())
        }
    }

    /** Nothing is drawn until the finger touches the sensor. */
    fun show() = stop()

    fun expand() = handler.post {
        handler.removeCallbacks(advance)
        if (frames.isEmpty()) return@post
        index = 0
        running = true
        advance.run()
    }

    fun hide() = stop()

    fun stop() = handler.post {
        handler.removeCallbacks(advance)
        running = false
        current = null
        index = 0
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val frame = current ?: return
        frame.setBounds(bounds)
        frame.alpha = alphaValue
        frame.draw(canvas)
    }

    override fun onBoundsChange(bounds: Rect) {
        current?.bounds = bounds
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = alphaValue

    override fun setColorFilter(colorFilter: ColorFilter?) {
        current?.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int =
        current?.intrinsicWidth ?: frames.firstOrNull()?.intrinsicWidth ?: -1

    override fun getIntrinsicHeight(): Int =
        current?.intrinsicHeight ?: frames.firstOrNull()?.intrinsicHeight ?: -1
}

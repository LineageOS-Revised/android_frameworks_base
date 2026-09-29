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
 * Plays a UDFPS unlock animation: a one-shot fade-in, a looping element animation while the
 * finger is down and a one-shot fade-out. All callbacks are marshalled onto the main thread.
 */
class UdfpsAnimationDrawable(
    private val fadeInFrames: List<Drawable>,
    private val fadeInDuration: Int,
    private val elementFrames: List<Drawable>,
    private val elementDuration: Int,
    private val fadeOutFrames: List<Drawable>,
    private val fadeOutDuration: Int,
) : Drawable() {

    private val handler = Handler(Looper.getMainLooper())
    private var frames: List<Drawable> = emptyList()
    private var frameDuration: Int = 16
    private var looping: Boolean = false
    private var index: Int = 0
    private var running: Boolean = false
    private var current: Drawable? = null
    private var alphaValue: Int = 255

    private val advance = object : Runnable {
        override fun run() {
            val list = frames
            if (!running || list.isEmpty()) return
            if (index >= list.size) {
                if (looping) index = 0 else return
            }
            current = list[index]
            invalidateSelf()
            index++
            handler.postDelayed(this, frameDuration.toLong())
        }
    }

    fun show() = handler.post { play(fadeInFrames, fadeInDuration, loop = false) }

    fun expand() = handler.post { play(elementFrames, elementDuration, loop = true) }

    fun hide() = handler.post { play(fadeOutFrames, fadeOutDuration, loop = false) }

    fun stop() = handler.post {
        handler.removeCallbacks(advance)
        running = false
        current = null
        index = 0
        invalidateSelf()
    }

    private fun play(list: List<Drawable>, duration: Int, loop: Boolean) {
        handler.removeCallbacks(advance)
        if (list.isEmpty()) {
            stop()
            return
        }
        frames = list
        frameDuration = duration
        looping = loop
        index = 0
        running = true
        advance.run()
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

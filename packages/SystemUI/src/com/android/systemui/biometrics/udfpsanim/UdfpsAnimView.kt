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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.View

/** Draws the selected UDFPS animation centred on the sensor. */
class UdfpsAnimView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var animation: UdfpsAnimationDrawable? = null
    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var offsetY: Int = 0

    init {
        setWillNotDraw(false)
    }

    fun setAnimation(drawable: UdfpsAnimationDrawable?) {
        animation?.stop()
        animation = drawable
        drawable?.callback = this
        invalidate()
    }

    fun setAnimationOffsetY(offsetY: Int) {
        this.offsetY = offsetY
        invalidate()
    }

    fun setSensorCenter(x: Float, y: Float) {
        centerX = x
        centerY = y + offsetY
        invalidate()
    }

    fun show() = animation?.show()

    fun expand() = animation?.expand()

    fun hide() = animation?.hide()

    fun stop() = animation?.stop()

    override fun verifyDrawable(who: Drawable): Boolean =
        who === animation || super.verifyDrawable(who)

    override fun onDraw(canvas: Canvas) {
        val drawable = animation ?: return
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        if (width <= 0 || height <= 0) return
        if (!loggedFirstDraw) {
            loggedFirstDraw = true
            Log.d(TAG, "drawing animation ${width}x$height at ($centerX, $centerY)")
        }
        drawable.bounds = Rect(
            (centerX - width / 2f).toInt(),
            (centerY - height / 2f).toInt(),
            (centerX + width / 2f).toInt(),
            (centerY + height / 2f).toInt(),
        )
        drawable.draw(canvas)
    }

    private var loggedFirstDraw = false

    companion object {
        private const val TAG = "UdfpsAnimView"
    }
}

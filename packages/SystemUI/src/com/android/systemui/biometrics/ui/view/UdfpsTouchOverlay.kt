/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.systemui.biometrics.ui.view

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.widget.FrameLayout
import com.android.systemui.biometrics.UdfpsDisplayModeProvider
import com.android.systemui.biometrics.UdfpsSurfaceView
import com.android.systemui.biometrics.udfpsanim.UdfpsAnimView
import com.android.systemui.biometrics.udfpsanim.UdfpsAnimationDrawable
import com.android.systemui.res.R

/**
 * A translucent (not visible to the user) view that receives touches to send to FingerprintManager
 * for fingerprint authentication.
 */
class UdfpsTouchOverlay(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    private var ghbmView: UdfpsSurfaceView? = null
    private var animView: UdfpsAnimView? = null
    private var udfpsDisplayMode: UdfpsDisplayModeProvider? = null

    // sensorRect may be bigger than the sensor. True sensor dimensions are defined in
    // overlayParams.sensorBounds
    var sensorRect = Rect()
        set(value) {
            field = value
            animView?.setSensorCenter(value.exactCenterX(), value.exactCenterY())
        }

    /** True after the call to [configureDisplay] and before the call to [unconfigureDisplay]. */
    var isDisplayConfigured: Boolean = false
        private set

    override fun onFinishInflate() {
        ghbmView = findViewById(R.id.hbm_view)
        animView = findViewById(R.id.udfps_anim_view)
    }

    /** Attaches the unlock animation; a null drawable keeps the AOSP default. */
    fun setUdfpsAnimation(drawable: UdfpsAnimationDrawable?, offsetY: Int) {
        Log.d(
            TAG,
            "setUdfpsAnimation drawable=${drawable != null} animView=${animView != null}" +
                " attached=$isAttachedToWindow offsetY=$offsetY",
        )
        animView?.setAnimation(drawable)
        animView?.setAnimationOffsetY(offsetY)
        animView?.visibility = if (drawable == null) GONE else VISIBLE
        if (drawable != null) {
            // The optical HBM surface is kept above the window by default, which would hide the
            // animation behind the illumination dot. Drop it below the UI only while animating.
            ghbmView?.setZOrderOnTop(false)
        }
        if (drawable != null && isAttachedToWindow) {
            animView?.show()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animView?.expand()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animView?.hide()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG, "onAttachedToWindow animView=${animView != null}")
        animView?.show()
    }

    override fun onDetachedFromWindow() {
        animView?.stop()
        super.onDetachedFromWindow()
    }

    fun setUdfpsDisplayModeProvider(udfpsDisplayModeProvider: UdfpsDisplayModeProvider?) {
        udfpsDisplayMode = udfpsDisplayModeProvider
    }

    fun configureDisplay(onDisplayConfigured: Runnable) {
        isDisplayConfigured = true
        val gView = ghbmView
        if (gView != null) {
            gView.setGhbmIlluminationListener(this::doIlluminate)
            gView.visibility = VISIBLE
            gView.startGhbmIllumination(onDisplayConfigured)
        } else {
            doIlluminate(null /* surface */, onDisplayConfigured)
        }
    }

    private fun doIlluminate(surface: Surface?, onDisplayConfigured: Runnable?) {
        udfpsDisplayMode?.enable {
            onDisplayConfigured?.run()
            ghbmView?.drawIlluminationDot(RectF(sensorRect))
        }
    }

    fun unconfigureDisplay() {
        isDisplayConfigured = false
        ghbmView?.let { view ->
            view.setGhbmIlluminationListener(null)
            view.visibility = INVISIBLE
        }
        udfpsDisplayMode?.disable(null /* onDisabled */)
    }

    companion object {
        private const val TAG = "UdfpsAnimView"
    }
}

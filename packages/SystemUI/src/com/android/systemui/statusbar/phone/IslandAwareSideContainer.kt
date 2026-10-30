/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.phone

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Status bar side container that shrinks its content to fit while the dynamic island takes up the
 * middle of the status bar, instead of letting the island cover it. Below [MIN_SCALE] the content
 * falls back to its own overflow handling.
 */
class IslandAwareSideContainer
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    var isIslandActive = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val content = singleVisibleChild()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val available = width - paddingLeft - paddingRight
        // The content gets measured at changing widths. Skip the measure cache, otherwise views
        // like StatusIconContainer keep their overflow decision from an earlier width.
        content?.let(::forceLayoutTree)
        if (
            !isIslandActive ||
                content == null ||
                available <= 0 ||
                MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
        ) {
            content?.setContentScale(1f)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        // One measure with room for the smallest scale: content that fits is measured as it
        // would be at full size, content that doesn't gets shrunk to fit.
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(
                (available / MIN_SCALE).toInt() + paddingLeft + paddingRight,
                MeasureSpec.EXACTLY,
            ),
            heightMeasureSpec,
        )
        setMeasuredDimension(width, measuredHeight)
        val natural = content.measuredWidth
        content.setContentScale(
            if (natural <= available) 1f
            else (available.toFloat() / natural).coerceAtLeast(MIN_SCALE)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val content = singleVisibleChild() ?: return
        val gravity = (content.layoutParams as? LayoutParams)?.gravity ?: Gravity.NO_GRAVITY
        val horizontal =
            Gravity.getAbsoluteGravity(
                if (gravity == Gravity.NO_GRAVITY) Gravity.START else gravity,
                layoutDirection,
            ) and Gravity.HORIZONTAL_GRAVITY_MASK
        content.pivotX = if (horizontal == Gravity.RIGHT) content.width.toFloat() else 0f
        content.pivotY = content.height / 2f
    }

    private fun forceLayoutTree(view: View) {
        view.forceLayout()
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) forceLayoutTree(view.getChildAt(i))
        }
    }

    private fun singleVisibleChild(): View? {
        var found: View? = null
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            if (found != null) return null
            found = child
        }
        return found
    }

    private fun View.setContentScale(scale: Float) {
        if (scaleX != scale) scaleX = scale
        if (scaleY != scale) scaleY = scale
    }

    private companion object {
        const val MIN_SCALE = 0.75f
    }
}

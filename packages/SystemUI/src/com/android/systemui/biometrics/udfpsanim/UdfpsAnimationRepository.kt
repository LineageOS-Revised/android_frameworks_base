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
import android.content.om.OverlayManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.SystemProperties
import android.os.UserHandle
import android.text.TextUtils
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import lineageos.providers.LineageSettings
import javax.inject.Inject

/** A single installed OxygenOS UDFPS animation overlay. */
data class UdfpsAnimationInfo(
    val packageName: String,
    val name: CharSequence,
    val order: Int,
)

/**
 * Discovers the OxygenOS UDFPS animation overlays and turns the selected one into a frame
 * animation. Frame drawables are read straight from the overlay package so we don't depend on
 * OMS merging them into SystemUI's resource table.
 */
@SysUISingleton
class UdfpsAnimationRepository @Inject constructor(
    @Application private val context: Context,
) {
    private val overlayManager: OverlayManager? =
        context.getSystemService(OverlayManager::class.java)

    /** Only devices that ship the animation overlays advertise support. */
    val isSupported: Boolean
        get() = SystemProperties.getBoolean(PROP_ANIMATIONS, false)

    fun getInstalledAnimations(): List<UdfpsAnimationInfo> {
        if (!isSupported) return emptyList()
        val manager = overlayManager ?: return emptyList()
        val result = ArrayList<UdfpsAnimationInfo>()
        try {
            val overlays = manager.getOverlayInfosForTarget(SYSTEMUI_PACKAGE, UserHandle.CURRENT)
            overlays?.forEach { info ->
                if (info.category != CATEGORY) return@forEach
                val appInfo = applicationInfo(info.packageName) ?: return@forEach
                result.add(
                    UdfpsAnimationInfo(
                        packageName = info.packageName,
                        name = appInfo.loadLabel(context.packageManager),
                        order = appInfo.metaData?.getInt(METADATA_ORDER, 0) ?: 0,
                    )
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to list UDFPS animations", t)
        }
        return result.sortedBy { it.order }
    }

    fun getSelectedPackage(): String? =
        LineageSettings.Secure.getString(
            context.contentResolver,
            LineageSettings.Secure.UDFPS_ANIMATION_STYLE,
        )

    fun setSelectedPackage(packageName: String?) {
        LineageSettings.Secure.putString(
            context.contentResolver,
            LineageSettings.Secure.UDFPS_ANIMATION_STYLE,
            packageName,
        )
    }

    /** The unlock animation for the selected style, or null to keep the AOSP default. */
    fun buildSelectedAnimation(): UdfpsAnimationDrawable? {
        if (!isSupported) return null
        val packageName = getSelectedPackage()
        if (TextUtils.isEmpty(packageName)) {
            Log.d(TAG, "No UDFPS animation selected, using default")
            return null
        }
        Log.d(TAG, "Building UDFPS animation for '$packageName'")
        return try {
            buildAnimation(packageName!!)?.also {
                Log.d(TAG, "Loaded UDFPS animation for '$packageName'")
            } ?: run {
                Log.w(TAG, "No frames found for '$packageName'")
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to load UDFPS animation $packageName", t)
            null
        }
    }

    /** Vertical offset (px) for the animation, overridable by a SystemUI overlay dimen. */
    fun getAnimationOffsetPx(): Int =
        context.resources.getDimensionPixelSize(R.dimen.udfps_animation_offset)

    private fun buildAnimation(packageName: String): UdfpsAnimationDrawable? {
        val res = context.packageManager.getResourcesForApplication(packageName)

        var elementBase = stringRes(res, packageName, "kgd_osfingerprint_element_60fps_anim_name")
            ?: DEFAULT_ELEMENT_BASE
        var elementCount =
            intRes(res, packageName, "kgd_osfingerprint_element_60fps_anim_frames", 0)
        if (elementCount == 0) {
            elementBase = DEFAULT_ELEMENT_BASE
            elementCount = intRes(res, packageName, "kgd_osfingerprint_element_anim_frames", 0)
        }

        val element = loadFrames(res, packageName, elementBase, elementCount)
        if (element.isEmpty()) return null

        return UdfpsAnimationDrawable(
            fadeInFrames = loadFrames(
                res, packageName, "kgd_osfingerprint_fadein",
                intRes(res, packageName, "kgd_osfingerprint_fadein_anim_frames", 0),
            ),
            fadeInDuration = rateToDuration(
                intRes(res, packageName, "kgd_osfingerprint_fadein_anim_rate", DEFAULT_RATE)
            ),
            elementFrames = element,
            elementDuration = rateToDuration(
                intRes(res, packageName, "kgd_osfingerprint_element_anim_rate", DEFAULT_RATE)
            ),
            fadeOutFrames = loadFrames(
                res, packageName, "kgd_osfingerprint_fadeout",
                intRes(res, packageName, "kgd_osfingerprint_fadeout_anim_frames", 0),
            ),
            fadeOutDuration = rateToDuration(
                intRes(res, packageName, "kgd_osfingerprint_fadeout_anim_rate", DEFAULT_RATE)
            ),
        )
    }

    private fun loadFrames(
        res: Resources,
        packageName: String,
        base: String,
        count: Int,
    ): List<Drawable> {
        if (count <= 0) return emptyList()
        val frames = ArrayList<Drawable>(count)
        for (i in 0 until count) {
            val id = res.getIdentifier("${base}_$i", "drawable", packageName)
            if (id == 0) break
            frames.add(res.getDrawable(id, null))
        }
        return frames
    }

    private fun intRes(res: Resources, packageName: String, name: String, def: Int): Int {
        val id = res.getIdentifier(name, "integer", packageName)
        return if (id == 0) def else res.getInteger(id)
    }

    private fun stringRes(res: Resources, packageName: String, name: String): String? {
        val id = res.getIdentifier(name, "string", packageName)
        return if (id == 0) null else res.getString(id)
    }

    private fun applicationInfo(packageName: String): ApplicationInfo? =
        try {
            context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

    private fun rateToDuration(rate: Int): Int = when (rate) {
        0 -> 64
        1 -> 48
        2 -> 33
        3 -> 20
        4 -> 42
        else -> 33
    }

    companion object {
        private const val TAG = "UdfpsAnimation"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private const val CATEGORY = "category.systemui.fingerprint.anim"
        private const val METADATA_ORDER = "order"
        private const val DEFAULT_ELEMENT_BASE = "kgd_fingerprint_element"
        private const val DEFAULT_RATE = 2
        const val PROP_ANIMATIONS = "ro.udfps.animations"
    }
}

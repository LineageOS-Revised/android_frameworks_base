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
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Overlay showing the display refresh rate measured by the kernel. */
@SysUISingleton
class FpsInfoOverlay
@Inject
constructor(
    context: Context,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
) : InfoOverlay(context, Handler(backgroundLooper), mainHandler) {

    // Keep the FPS overlay away from the (wider) CPU overlay.
    override val windowGravity: Int = Gravity.TOP or Gravity.END

    /** Sysfs nodes that may expose the measured fps, in order of preference. */
    private val nodePaths = context.resources.getStringArray(R.array.config_fpsInfoNodePaths)

    private var nodePath: String? = null

    override fun refresh(): CharSequence {
        val fps = readNode()?.let { FPS_PATTERN.find(it)?.value }
        return if (fps != null) {
            context.getString(R.string.fps_info_overlay_text, fps)
        } else {
            context.getString(R.string.fps_info_overlay_unavailable)
        }
    }

    private fun readNode(): String? {
        // Reuse the node that worked before, fall back to probing the other ones.
        nodePath?.let { path ->
            readFile(path)?.let {
                return it
            }
            nodePath = null
        }
        for (path in nodePaths) {
            readFile(path)?.let {
                nodePath = path
                return it
            }
        }
        return null
    }

    private fun readFile(path: String): String? =
        try {
            File(path).readText()
        } catch (e: IOException) {
            null
        }

    private companion object {
        val FPS_PATTERN = Regex("""\d+(?:\.\d+)?""")
    }
}

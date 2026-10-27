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

package com.android.systemui.qs.tiles;

import static com.android.internal.logging.MetricsLogger.VIEW_UNKNOWN;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.service.quicksettings.Tile;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.quickactions.sysmon.data.SystemMonitorRepository;

import javax.inject.Inject;

import lineageos.providers.LineageSettings;

/** Quick settings tile: system monitor in the dynamic island **/
public class SystemMonitorTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "system_monitor";

    private final SystemMonitorRepository mRepository;

    @Nullable
    private Icon mIcon = null;

    @Inject
    public SystemMonitorTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            SystemMonitorRepository repository
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mRepository = repository;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mRepository.setOpen(false);
    }

    @Override
    public void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
        if (listening) {
            refreshState();
        }
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        if (!isAvailableNow()) {
            return;
        }
        mRepository.setOpen(!mRepository.isOpen().getValue());
        refreshState();
    }

    private boolean isAvailableNow() {
        return isEnabled(LineageSettings.System.STATUS_BAR_SHOW_DYNAMIC_ISLAND, 0)
                && isEnabled(LineageSettings.System.STATUS_BAR_DYNAMIC_ISLAND_SYSTEM_MONITOR, 1);
    }

    private boolean isEnabled(String key, int def) {
        return LineageSettings.System.getIntForUser(mContext.getContentResolver(), key, def,
                UserHandle.USER_CURRENT) != 0;
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_system_monitor_label);
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        boolean available = isAvailableNow();
        state.value = available && mRepository.isOpen().getValue();
        if (mIcon == null) {
            mIcon = maybeLoadResourceIcon(R.drawable.ic_qs_system_monitor);
        }
        state.icon = mIcon;
        state.label = mContext.getString(R.string.quick_settings_system_monitor_label);
        state.hasLongClickEffect = false;
        state.state = !available ? Tile.STATE_UNAVAILABLE
                : state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        state.contentDescription = state.label;
    }
}

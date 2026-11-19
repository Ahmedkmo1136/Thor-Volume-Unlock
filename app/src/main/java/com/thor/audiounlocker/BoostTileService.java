package com.thor.audiounlocker;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class BoostTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        sync();
    }

    @Override
    public void onClick() {
        super.onClick();
        Prefs prefs = new Prefs(this);
        boolean next = !prefs.isEnabled();
        prefs.setEnabled(next);
        if (next) {
            AudioBoostService.startOrUpdate(this);
        } else {
            AudioBoostService.stopServiceNow(this);
        }
        sync();
    }

    private void sync() {
        Tile tile = getQsTile();
        if (tile == null) return;
        Prefs prefs = new Prefs(this);
        boolean on = prefs.isEnabled();
        tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(on ? ("+" + prefs.getGainDb() + " dB") : "Off");
        tile.updateTile();
    }
}

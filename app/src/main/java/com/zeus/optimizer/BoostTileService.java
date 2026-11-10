package com.zeus.optimizer;

import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/** Botão "ZEUS Boost" nas configurações rápidas (cortina de notificações). */
public class BoostTileService extends TileService {

    private boolean running;

    @Override
    public void onStartListening() {
        setState(Tile.STATE_INACTIVE, "ZEUS Boost");
    }

    @Override
    public void onClick() {
        if (running) return;
        running = true;
        setState(Tile.STATE_ACTIVE, "Otimizando...");
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            long before = Optimizer.availMem(this);
            int n = Optimizer.killBackground(this);
            Optimizer.clearOwnCache(this);
            try {
                Thread.sleep(900);
            } catch (InterruptedException ignored) {
            }
            long freed = Math.max(0, Optimizer.availMem(this) - before);
            ui.post(() -> {
                running = false;
                setState(Tile.STATE_INACTIVE, "ZEUS Boost");
                String msg = "⚡ ZEUS: " + n + " apps encerrados"
                        + (freed > 0 ? " • " + Theme.size(freed) + " de RAM liberada" : "");
                try {
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                } catch (Exception ignored) {
                }
            });
        }).start();
    }

    private void setState(int state, String label) {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(state);
        t.setLabel(label);
        t.updateTile();
    }
}

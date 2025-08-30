// Metrics.java — drop-in para Android (Java). Sin permisos extra.
// Genera metrics/android_metrics.csv en getExternalFilesDir("metrics")
// Registra cada 5 s: CPU% del proceso, heap usado, heap máximo, bytes TX/RX y energy_index relativo.
package com.example.metrics;

import android.content.Context;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;

public class Metrics {
    private static final String TAG = "LAB_METRICS";
    private static final long PERIOD_MS = 5000;
    private static Handler h;
    private static FileWriter fw;
    private static long lastWallMs, lastCpuMs, lastTx, lastRx;

    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                long now = System.currentTimeMillis();
                long wallDt = now - lastWallMs;
                long cpuNowMs = Process.getElapsedCpuTime();
                long cpuDt = cpuNowMs - lastCpuMs;

                int uid = Process.myUid();
                long tx = TrafficStats.getUidTxBytes(uid);
                long rx = TrafficStats.getUidRxBytes(uid);
                long txDt = (tx < 0 || lastTx < 0) ? 0 : (tx - lastTx);
                long rxDt = (rx < 0 || lastRx < 0) ? 0 : (rx - lastRx);

                double cpuPct = Math.min(100.0, Math.max(0.0, (wallDt > 0 ? (100.0 * cpuDt / wallDt) : 0.0)));
                long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                long max  = Runtime.getRuntime().maxMemory();

                double energyIndex = cpuPct + 0.0005 * (txDt + rxDt);

                String line = String.format("%d,%.1f,%d,%d,%d,%d,%.2f\n",
                        now, cpuPct, used, max, txDt, rxDt, energyIndex);

                fw.write(line); fw.flush();
                Log.i(TAG, line.trim());

                lastWallMs = now; lastCpuMs = cpuNowMs; lastTx = tx; lastRx = rx;
            } catch (Exception e) {
                Log.e(TAG, "metrics tick", e);
            } finally {
                h.postDelayed(this, PERIOD_MS);
            }
        }
    };

    public static void start(Context ctx) {
        try {
            File dir = ctx.getExternalFilesDir("metrics");
            if (dir != null && !dir.exists()) dir.mkdirs();
            File f = new File(dir, "android_metrics.csv");
            fw = new FileWriter(f, false);
            fw.write("ts_ms,cpu_pct,heap_used,max_heap,tx_bytes,rx_bytes,energy_index\n");
            lastWallMs = System.currentTimeMillis();
            lastCpuMs  = Process.getElapsedCpuTime();
            int uid = Process.myUid();
            lastTx = TrafficStats.getUidTxBytes(uid);
            lastRx = TrafficStats.getUidRxBytes(uid);
            h = new Handler(Looper.getMainLooper());
            h.postDelayed(tick, PERIOD_MS);
        } catch (Exception e) {
            Log.e(TAG, "start metrics", e);
        }
    }

    public static void stop() {
        try { if (h != null) h.removeCallbacksAndMessages(null); } catch (Exception ignored) {}
        try { if (fw != null) { fw.flush(); fw.close(); } } catch (Exception ignored) {}
    }
}

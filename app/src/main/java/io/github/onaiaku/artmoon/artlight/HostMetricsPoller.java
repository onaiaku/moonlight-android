package io.github.onaiaku.artmoon.artlight;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import io.github.onaiaku.artmoon.LimeLog;
import io.github.onaiaku.artmoon.grid.PcGridAdapter;
import io.github.onaiaku.artmoon.nvstream.http.ComputerDetails;

import org.json.JSONObject;

/**
 * HostMetricsPoller — Android port of the desktop's HostMetricsPoller.
 * Polls STATS from ArtLight hosts (via ArtLightBridge) and paints the
 * live-metrics line on the matching host hero card. Port 47998 hosts only;
 * unreachable hosts simply keep the telemetry line hidden.
 *
 * Desktop parity: GPU %, encoder %, temp, VRAM used, CPU %, net TX.
 */
public class HostMetricsPoller {

    private static final long INTERVAL_MS = 2000;

    private final ArtLightBridge bridge;
    private final PcGridAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private boolean pollInFlight = false;

    private static final class PollTarget {
        final String uuid;
        final String address;
        final String name;

        PollTarget(ComputerDetails details) {
            this.uuid = details.uuid;
            this.name = details.name;
            // Prefer the reachable (active) address, then remote, then local.
            this.address =
                    (details.activeAddress != null) ? details.activeAddress.address :
                    (details.remoteAddress != null) ? details.remoteAddress.address :
                    (details.localAddress != null) ? details.localAddress.address : null;
        }
    }

    public HostMetricsPoller(Context context, PcGridAdapter adapter) {
        this.bridge = new ArtLightBridge(context);
        this.adapter = adapter;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        handler.post(pollRunnable);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(pollRunnable);
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            if (!pollInFlight) {
                pollInFlight = true;
                pollOnce();
            }
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    private void pollOnce() {
        // Find the first online host with a usable address. The desktop polls
        // the foreground host; the phone's foreground host is the first card.
        PollTarget target = null;
        for (int i = 0; i < adapter.getCount(); i++) {
            Object o = adapter.getItem(i);
            if (!(o instanceof io.github.onaiaku.artmoon.PcView.ComputerObject)) {
                continue;
            }
            ComputerDetails d = ((io.github.onaiaku.artmoon.PcView.ComputerObject) o).details;
            if (d.state == ComputerDetails.State.ONLINE) {
                target = new PollTarget(d);
                if (target.address != null) {
                    break;
                }
            }
        }

        if (target == null || target.address == null) {
            pollInFlight = false;
            return;
        }

        final PollTarget tgt = target;
        bridge.requestStats(tgt.address, new ArtLightBridge.ResponseCallback() {
            @Override
            public void onResult(final String response) {
                pollInFlight = false;
                final String text = formatStats(response, tgt.name);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!running) {
                            return;
                        }
                        adapter.updateTelemetryByUuid(tgt.uuid, text);
                    }
                });
            }
        });
    }

    /**
     * One-line summary for the hero card, desktop's fields:
     * GPU 45% · ENC 80% · 72°C · VRAM 4.2G · CPU 30% · NET 18Mb/s
     * Empty string on error/unreachable — the line hides, never fakes data.
     */
    private static String formatStats(String json, String hostName) {
        if (json == null || json.isEmpty() || "STATS_UNAVAILABLE".equals(json)) {
            return "";
        }
        try {
            JSONObject o = new JSONObject(json);
            StringBuilder sb = new StringBuilder();
            append(sb, "GPU ", o.optInt("gpu", -1), "%", true);
            append(sb, " · ENC ", o.optInt("gpu_enc", -1), "%", true);
            append(sb, " · ", o.optInt("gpu_temp", -1), "°C", true);
            if (o.has("vram_used")) {
                long vram = o.optLong("vram_used", 0);
                sb.append(" · VRAM ").append(String.format("%.1fG", vram / 1000.0));
            }
            append(sb, " · CPU ", o.optInt("cpu", -1), "%", true);
            if (o.has("net_tx")) {
                sb.append(" · NET ").append(o.optInt("net_tx", 0)).append("Mb/s");
            }
            return sb.toString();
        } catch (Exception e) {
            LimeLog.info("HostMetricsPoller: stats parse failed: " + e.getMessage());
            return "";
        }
    }

    private static void append(StringBuilder sb, String prefix, int value, String suffix, boolean skipIfMissing) {
        if (value < 0 && skipIfMissing) {
            return;
        }
        sb.append(prefix).append(value).append(suffix);
    }
}

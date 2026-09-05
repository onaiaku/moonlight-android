package io.github.onaiaku.artmoon.grid;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import io.github.onaiaku.artmoon.PcView;
import io.github.onaiaku.artmoon.R;
import io.github.onaiaku.artmoon.nvstream.http.ComputerDetails;
import io.github.onaiaku.artmoon.nvstream.http.PairingManager;
import io.github.onaiaku.artmoon.preferences.PreferenceConfiguration;

import java.util.Collections;
import java.util.Comparator;

public class PcGridAdapter extends GenericGridAdapter<PcView.ComputerObject> {

    private final java.util.HashMap<String, android.view.View> boundViews = new java.util.HashMap<>();

    public PcGridAdapter(Context context, PreferenceConfiguration prefs) {
        super(context, getLayoutIdForPreferences(prefs));
    }

    private static int getLayoutIdForPreferences(PreferenceConfiguration prefs) {
        return R.layout.pc_grid_item;
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        // This will trigger the view to reload with the new layout
        setLayoutId(getLayoutIdForPreferences(prefs));
    }

    @Override
    public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
        View v = super.getView(position, convertView, parent);
        PcView.ComputerObject obj = (PcView.ComputerObject) getItem(position);
        if (obj != null) {
            boundViews.put(obj.details.uuid, v);
        }
        return v;
    }

    @Override
    public void clear() {
        boundViews.clear();
        super.clear();
    }

    public void addComputer(PcView.ComputerObject computer) {
        itemList.add(computer);
        sortList();
    }

    private void sortList() {
        Collections.sort(itemList, new Comparator<PcView.ComputerObject>() {
            @Override
            public int compare(PcView.ComputerObject lhs, PcView.ComputerObject rhs) {
                return lhs.details.name.toLowerCase().compareTo(rhs.details.name.toLowerCase());
            }
        });
    }

    /**
     * ArtLight telemetry: update the live host-metrics line on the row bound
     * to this computer (STATS via ArtLightBridge). No-op if the view isn't
     * currently on screen. Called from the poller on the UI thread.
     */
    public void updateTelemetryByUuid(String uuid, String telemetryText) {
        View v = boundViews.get(uuid);
        if (v == null) {
            return;
        }
        TextView tel = v.findViewById(R.id.am_telemetry);
        if (tel != null) {
            if (telemetryText == null || telemetryText.isEmpty()) {
                tel.setVisibility(android.view.View.GONE);
            } else {
                tel.setText(telemetryText);
                tel.setVisibility(android.view.View.VISIBLE);
            }
        }
    }

    public View getViewForComputer(String uuid) {
        return boundViews.get(uuid);
    }

    public boolean removeComputer(PcView.ComputerObject computer) {
        return itemList.remove(computer);
    }

    @Override
    public void populateView(View parentView, ImageView imgView, ProgressBar prgView, TextView txtView, ImageView overlayView, PcView.ComputerObject obj) {
        imgView.setImageResource(R.drawable.ic_computer);

        // ArtMoon status dot + label, ported from the desktop's host card:
        // online green, pairing amber, offline red — always semantic, never the accent.
        View statusDot = parentView.findViewById(R.id.status_dot);
        TextView statusLabel = parentView.findViewById(R.id.status_label);

        if (obj.details.state == ComputerDetails.State.ONLINE) {
            imgView.setAlpha(1.0f);
            if (statusDot != null) statusDot.setBackgroundResource(R.drawable.am_dot_online);
            if (statusLabel != null) {
                statusLabel.setText(obj.details.pairState == PairingManager.PairState.NOT_PAIRED
                        ? R.string.am_status_unpaired : R.string.am_status_online);
                statusLabel.setTextColor(context.getResources().getColor(R.color.am_online));
            }
        }
        else if (obj.details.state == ComputerDetails.State.UNKNOWN) {
            imgView.setAlpha(0.6f);
            if (statusDot != null) statusDot.setBackgroundResource(R.drawable.am_dot_pairing);
            if (statusLabel != null) {
                statusLabel.setText(R.string.am_status_checking);
                statusLabel.setTextColor(context.getResources().getColor(R.color.am_pairing));
            }
        }
        else {
            imgView.setAlpha(0.4f);
            if (statusDot != null) statusDot.setBackgroundResource(R.drawable.am_dot_offline);
            if (statusLabel != null) {
                statusLabel.setText(R.string.am_status_offline);
                statusLabel.setTextColor(context.getResources().getColor(R.color.am_offline));
            }
        }

        // Hero card: ready line + spec chips (approved render, spec section 2)
        TextView readyLine = parentView.findViewById(R.id.am_ready_line);
        if (readyLine != null) {
            if (obj.details.state == ComputerDetails.State.ONLINE) {
                readyLine.setText(R.string.am_hero_ready);
                readyLine.setVisibility(View.VISIBLE);
            } else if (obj.details.state == ComputerDetails.State.OFFLINE) {
                readyLine.setText(R.string.am_hero_offline);
                readyLine.setVisibility(View.VISIBLE);
            } else {
                readyLine.setVisibility(View.GONE);
            }
        }

        TextView specRes = parentView.findViewById(R.id.am_spec_res);
        TextView specFps = parentView.findViewById(R.id.am_spec_fps);
        TextView specBitrate = parentView.findViewById(R.id.am_spec_bitrate);
        boolean online = obj.details.state == ComputerDetails.State.ONLINE;
        if (specRes != null && specFps != null && specBitrate != null) {
            if (online) {
                PreferenceConfiguration prefs = PreferenceConfiguration.readPreferences(context);
                specRes.setText(prefs.width + "\u00d7" + prefs.height);
                specFps.setText(prefs.fps + " FPS");
                specBitrate.setText(prefs.bitrate + " Mbps");
                specRes.setVisibility(View.VISIBLE);
                specFps.setVisibility(View.VISIBLE);
                specBitrate.setVisibility(View.VISIBLE);
            } else {
                specRes.setVisibility(View.GONE);
                specFps.setVisibility(View.GONE);
                specBitrate.setVisibility(View.GONE);
            }
        }

        if (obj.details.state == ComputerDetails.State.UNKNOWN) {
            prgView.setVisibility(View.VISIBLE);
        }
        else {
            prgView.setVisibility(View.INVISIBLE);
        }

        txtView.setText(obj.details.name);
        if (obj.details.state == ComputerDetails.State.ONLINE) {
            txtView.setAlpha(1.0f);
        }
        else {
            txtView.setAlpha(0.4f);
        }

        if (obj.details.state == ComputerDetails.State.OFFLINE) {
            overlayView.setImageResource(R.drawable.ic_pc_offline);
            overlayView.setAlpha(0.4f);
            overlayView.setVisibility(View.VISIBLE);
        }
        // We must check if the status is exactly online and unpaired
        // to avoid colliding with the loading spinner when status is unknown
        else if (obj.details.state == ComputerDetails.State.ONLINE &&
                obj.details.pairState == PairingManager.PairState.NOT_PAIRED) {
            overlayView.setImageResource(R.drawable.ic_lock);
            overlayView.setAlpha(1.0f);
            overlayView.setVisibility(View.VISIBLE);
        }
        else {
            overlayView.setVisibility(View.GONE);
        }
    }
}

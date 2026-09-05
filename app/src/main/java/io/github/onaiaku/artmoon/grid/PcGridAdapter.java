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
import io.github.onaiaku.artmoon.nvstream.jni.MoonBridge;
import io.github.onaiaku.artmoon.preferences.PreferenceConfiguration;

import java.util.Collections;
import java.util.Comparator;

public class PcGridAdapter extends GenericGridAdapter<PcView.ComputerObject> {

    private final java.util.HashMap<String, android.view.View> boundViews = new java.util.HashMap<>();

    /** Optional hook to the owning PcView so card actions can reuse its flows. */
    private io.github.onaiaku.artmoon.PcView pcView;

    public void setPcView(io.github.onaiaku.artmoon.PcView view) {
        this.pcView = view;
    }

    public PcGridAdapter(Context context, PreferenceConfiguration prefs) {
        super(context, getLayoutIdForPreferences(prefs));
    }

    /**
     * v10: the hero card must FILL the viewport between the picker row and
     * the footer (approved render: no dead gap below the card). GridView
     * ignores a child's match_parent, so we measure the viewport from the
     * parent and set the row height explicitly on first bind.
     */
    @Override
    public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
        View v = super.getView(position, convertView, parent);
        if (parent != null && parent.getHeight() > 0
                && v.getLayoutParams().height != parent.getHeight()) {
            android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.height = parent.getHeight();
            v.setLayoutParams(lp);
        }
        PcView.ComputerObject obj = (PcView.ComputerObject) getItem(position);
        if (obj != null) {
            boundViews.put(obj.details.uuid, v);
        }
        return v;
    }

    private static int getLayoutIdForPreferences(PreferenceConfiguration prefs) {
        return R.layout.pc_grid_item;
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        // This will trigger the view to reload with the new layout
        setLayoutId(getLayoutIdForPreferences(prefs));
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

    public View getViewForComputer(String uuid) {
        return boundViews.get(uuid);
    }

    /**
     * v10: paint the right-column stat blocks (RTT / GPU / NET) on the card
     * bound to this host. Values may be null — a null hides its block and,
     * if ALL are null, the whole stats column hides. Never faked.
     */
    public void updateStatsByUuid(String uuid, Integer rttMs, Integer gpuPercent, Integer netMbps) {
        View v = boundViews.get(uuid);
        if (v == null) {
            return;
        }
        android.view.View col = v.findViewById(R.id.am_stats_col);
        if (col == null) {
            return;
        }
        boolean any = false;
        any |= bindStat(v, R.id.am_stat_rtt_group, R.id.am_stat_rtt_value,
                rttMs == null ? null : rttMs + " ms");
        any |= bindStat(v, R.id.am_stat_gpu_group, R.id.am_stat_gpu_value,
                gpuPercent == null ? null : gpuPercent + "%");
        any |= bindStat(v, R.id.am_stat_net_group, R.id.am_stat_net_value,
                netMbps == null ? null : netMbps + "Mb/s");
        col.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    private static boolean bindStat(View parent, int groupId, int valueId, String value) {
        android.view.View group = parent.findViewById(groupId);
        if (group == null) {
            return false;
        }
        if (value == null) {
            group.setVisibility(View.GONE);
            return false;
        }
        TextView tv = parent.findViewById(valueId);
        if (tv != null) {
            tv.setText(value);
        }
        group.setVisibility(View.VISIBLE);
        return true;
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
        TextView specCodec = parentView.findViewById(R.id.am_spec_codec);
        TextView specAudio = parentView.findViewById(R.id.am_spec_audio);
        boolean online = obj.details.state == ComputerDetails.State.ONLINE;
        if (specRes != null && specFps != null && specBitrate != null) {
            if (online) {
                PreferenceConfiguration prefs = PreferenceConfiguration.readPreferences(context);
                specRes.setText(prefs.width + "\u00d7" + prefs.height);
                specFps.setText(prefs.fps + " FPS");
                specBitrate.setText((prefs.bitrate + 999) / 1000 + " Mbps");
                if (specCodec != null) {
                    specCodec.setText(codecLabel(prefs.videoFormat));
                }
                if (specAudio != null) {
                    specAudio.setText(audioLabel(prefs.audioConfiguration));
                }
                TextView[] chips = {specRes, specFps, specBitrate, specCodec, specAudio};
                for (TextView c : chips) {
                    if (c != null) c.setVisibility(View.VISIBLE);
                }
            } else {
                TextView[] chips = {specRes, specFps, specBitrate, specCodec, specAudio};
                for (TextView c : chips) {
                    if (c != null) c.setVisibility(View.GONE);
                }
            }
        }

        if (obj.details.state == ComputerDetails.State.UNKNOWN) {
            prgView.setVisibility(View.VISIBLE);
        }
        else {
            prgView.setVisibility(View.INVISIBLE);
        }

        // v10: bind the Open/Options action row to the same flows as the
        // grid tap (paired -> app list, unpaired -> pairing, offline/unknown
        // -> context menu). The hero card's buttons must never dead-end.
        if (pcView != null) {
            pcView.bindHeroCardActions(parentView, obj);
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
    private static String codecLabel(PreferenceConfiguration.FormatOption fmt) {
        if (fmt == PreferenceConfiguration.FormatOption.FORCE_AV1) return "AV1";
        if (fmt == PreferenceConfiguration.FormatOption.FORCE_HEVC) return "HEVC";
        if (fmt == PreferenceConfiguration.FormatOption.FORCE_H264) return "H.264";
        return "Auto";
    }

    private static String audioLabel(MoonBridge.AudioConfiguration audio) {
        if (audio == MoonBridge.AUDIO_CONFIGURATION_51_SURROUND) return "5.1";
        if (audio == MoonBridge.AUDIO_CONFIGURATION_71_SURROUND) return "7.1";
        return "Stereo";
    }
}

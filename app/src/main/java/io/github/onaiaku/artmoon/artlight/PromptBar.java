package io.github.onaiaku.artmoon.artlight;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PromptBar — re-renders a screen's footer prompt pills to match the active
 * input mode (desktop parity: prompts always tell the truth about the device
 * driving the UI).
 *
 * Each screen registers its pills once with (view, keycapText, label)
 * triples; when the input mode changes (or the bar is first attached) every
 * pill's text is rewritten:
 *   TOUCH    → keycap "✓", label plain ("Settings", "Shutdown", "Exit")
 *   GAMEPAD  → keycap glyph (Y / B / A ...), label unchanged
 *   KEYBOARD → keycap letter (S / P / Esc ...), label unchanged
 *
 * Pills are matched by their stable resource id name so screens don't need
 * custom subclasses — register with R.id values and strings resolved here.
 */
public final class PromptBar {

    /** One prompt pill: the keycap TextView and its meaning. */
    public static class Pill {
        public final TextView keycap;
        /** Semantic action: "settings" | "shutdown" | "exit" | "hosts" | "back" */
        public final String action;

        public Pill(TextView keycap, String action) {
            this.keycap = keycap;
            this.action = action;
        }
    }

    private final Map<String, Pill> pills = new LinkedHashMap<>();
    private final Map<String, android.graphics.drawable.Drawable> origBg = new LinkedHashMap<>();
    private final Activity activity;
    private boolean registered = false;

    public PromptBar(Activity activity) {
        this.activity = activity;
        InputModeManager.get().addListener(new InputModeManager.PromptRefreshListener() {
            @Override
            public void onInputModeChanged(InputModeManager.Mode newMode) {
                refresh(newMode);
            }
        });
    }

    /** Register one pill. Safe to call again; later registration wins. */
    public void register(TextView keycap, String action) {
        if (keycap != null) {
            if (!origBg.containsKey(action)) {
                origBg.put(action, keycap.getBackground());
            }
            pills.put(action, new Pill(keycap, action));
        }
    }

    /** Register by view id lookup (null-safe — portrait layouts may lack pills). */
    public void registerById(int viewId, String action) {
        View v = activity.findViewById(viewId);
        if (v instanceof TextView) {
            register((TextView) v, action);
        }
    }

    /** First paint + mark as live. Call after all registrations. */
    public void attach() {
        registered = true;
        refresh(InputModeManager.get().getMode());
    }

    public boolean isAttached() {
        return registered;
    }

    private void refresh(InputModeManager.Mode mode) {
        if (!registered) {
            return;
        }
        for (Pill p : pills.values()) {
            if (p.keycap == null) {
                continue;
            }
            p.keycap.setText(textFor(mode, p.action));
            if (mode == InputModeManager.Mode.GAMEPAD) {
                // Coloured face-button icon (desktop parity): letter sits on
                // the pad button's colour instead of the flat pill.
                p.keycap.setBackgroundResource(gamepadBg(p.action));
                p.keycap.setTextColor(0xFF1A1D22); // dark lettering on bright pad colours
            } else {
                android.graphics.drawable.Drawable bg = origBg.get(p.action);
                if (bg != null) {
                    p.keycap.setBackground(bg);
                }
                p.keycap.setTextColor(0xFFFFFFFF);
            }
        }
    }

    private static int gamepadBg(String action) {
        if ("settings".equals(action)) {
            return io.github.onaiaku.artmoon.R.drawable.am_pad_y; // Y = settings, yellow
        }
        if ("shutdown".equals(action)) {
            return io.github.onaiaku.artmoon.R.drawable.am_pad_power;
        }
        // exit / back / hosts → red B
        return io.github.onaiaku.artmoon.R.drawable.am_pad_b;
    }

    private static String textFor(InputModeManager.Mode mode, String action) {
        switch (mode) {
            case GAMEPAD:
                return gamepadGlyph(action);
            case KEYBOARD:
                return keyboardKey(action);
            case TOUCH:
            default:
                // Touch: no hardware affordance — show a tap glyph
                return "✓";
        }
    }

    private static String gamepadGlyph(String action) {
        // Desktop mapping (ArtMoon SettingsScreen/HostStage prompts):
        //   Y = settings, P(view)/Start = power, B = back/exit, A = select
        if ("settings".equals(action)) {
            return "Y";
        }
        if ("shutdown".equals(action)) {
            return "⟳"; // shoulder/start power affordance — no standard glyph
        }
        if ("exit".equals(action) || "back".equals(action) || "hosts".equals(action)) {
            return "B";
        }
        return "A";
    }

    private static String keyboardKey(String action) {
        if ("settings".equals(action)) {
            return "S";
        }
        if ("shutdown".equals(action)) {
            return "P";
        }
        if ("exit".equals(action) || "back".equals(action) || "hosts".equals(action)) {
            return "Esc";
        }
        return "Enter";
    }
}

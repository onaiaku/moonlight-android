package io.github.onaiaku.artmoon.artlight;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import io.github.onaiaku.artmoon.LimeLog;

/**
 * HostAuthManager — Android port of the desktop's enrollment flow
 * (ComputerModel's CAPS probe + ENROLL + PIN lifecycle).
 *
 * States reported per host via Listener:
 *   "none"       — not an ArtLight host (no CAPS1 reply)
 *   "open"       — ArtLight host with auth=optional (works without approval)
 *   "authorized" — enrolled and approved
 *   "pending"    — waiting for the user to click Allow on the host; PIN shown
 *   "denied"     — host user declined; a later retry generates a fresh PIN
 */
public class HostAuthManager {

    public interface Listener {
        void onAuthState(String uuid, String state, String pin);
    }

    private static final long RETRY_MS = 3000;
    private static final int MAX_ATTEMPTS = 5;

    private final ArtLightBridge bridge;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final java.util.HashMap<String, String> pinsByUuid = new java.util.HashMap<>();
    private final java.util.HashMap<String, Listener> listeners = new java.util.HashMap<>();

    public HostAuthManager(Context context) {
        this.bridge = new ArtLightBridge(context);
    }

    public void setListener(String uuid, Listener listener) {
        listeners.put(uuid, listener);
    }

    public void clearListener(String uuid) {
        listeners.remove(uuid);
    }

    public String getPin(String uuid) {
        return pinsByUuid.get(uuid);
    }

    /** User explicitly re-requested access after dismissing the popup. */
    public void forceRetry(String uuid) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                String pin = pinsByUuid.get(uuid);
                if (pin != null) {
                    notify(uuid, "pending", pin);
                }
            }
        });
    }

    public void probe(final String uuid, final String address) {
        bridge.requestCaps(address, new ArtLightBridge.ResponseCallback() {
            @Override
            public void onResult(String caps) {
                if (caps == null || !caps.startsWith("CAPS1")) {
                    notify(uuid, "none", null);
                    return;
                }
                if (caps.contains("auth=optional")) {
                    pinsByUuid.remove(uuid);
                    notify(uuid, "open", null);
                    return;
                }
                // auth required → enroll with a stable PIN while pending
                String pin = pinsByUuid.get(uuid);
                if (pin == null || pin.isEmpty()) {
                    pin = String.format("%04d", (int) (Math.random() * 10000));
                    pinsByUuid.put(uuid, pin);
                }
                enrollWithRetries(uuid, address, pin, MAX_ATTEMPTS);
            }
        });
    }

    private void enrollWithRetries(final String uuid, final String address,
                                   final String pin, final int attemptsLeft) {
        bridge.enroll(address, pin, new ArtLightBridge.ResponseCallback() {
            @Override
            public void onResult(String reply) {
                if ("ENROLLED".equals(reply)) {
                    pinsByUuid.remove(uuid);
                    notify(uuid, "authorized", null);
                } else if ("PENDING".equals(reply)) {
                    notify(uuid, "pending", pin);
                    if (attemptsLeft > 1) {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                enrollWithRetries(uuid, address, pin, attemptsLeft - 1);
                            }
                        }, RETRY_MS);
                    }
                } else if ("DENIED".equals(reply)) {
                    // PIN matters only while pending; drop it so a later
                    // re-request starts fresh (desktop parity).
                    pinsByUuid.remove(uuid);
                    notify(uuid, "denied", null);
                } else {
                    notify(uuid, "none", null);
                }
            }
        });
    }

    private void notify(final String uuid, final String state, final String pin) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Listener l = listeners.get(uuid);
                if (l != null) {
                    l.onAuthState(uuid, state, pin);
                }
            }
        });
    }
}

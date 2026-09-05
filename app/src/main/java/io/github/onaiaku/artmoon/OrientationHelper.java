package io.github.onaiaku.artmoon;

import android.app.Activity;

/**
 * Portrait lock for phones only. TVs (Shield) are landscape-native and must
 * never be locked. Spec: docs/android-ui-spec.md §1.
 */
public final class OrientationHelper {

    private OrientationHelper() {}

    /**
     * Call from every ArtMoon activity's onCreate: portrait-locks phones,
     * leaves TV/large-screen devices untouched.
     */
    public static void lockPortraitOnPhones(Activity activity) {
        int uiMode = activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_TYPE_MASK;
        boolean isTelevision = uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
                || activity.getPackageManager().hasSystemFeature("android.software.leanback");

        if (!isTelevision) {
            activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }
}

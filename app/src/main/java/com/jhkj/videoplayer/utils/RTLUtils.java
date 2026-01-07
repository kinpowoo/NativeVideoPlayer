package com.jhkj.videoplayer.utils;

import android.view.View;

import androidx.core.view.ViewCompat;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class RTLUtils {

    private static final Set<String> RTL;

    static {
        // Yiddish
        RTL = Set.of("ar", // Arabic
                "dv", // Divehi
                "fa", // Persian (Farsi)
                "ha", // Hausa
                "he", // Hebrew
                "iw", // Hebrew (old code)
                "ji", // Yiddish (old code)
                "ps", // Pashto, Pushto
                "ur", // Urdu
                "yi");
    }

    public static boolean isRTL(Locale locale) {
        if (locale == null)
            return false;

        // Character.getDirectionality(locale.getDisplayName().charAt(0))
        // can lead to NPE (Java 7 bug)
        // https://bugs.openjdk.java.net/browse/JDK-6992272?page=com.atlassian.streams.streams-jira-plugin:activity-stream-issue-tab
        // using hard coded list of locale instead
        return RTL.contains(locale.getLanguage());
    }

    public static boolean isRTL(View view) {
        if (view == null)
            return false;

        // config.getLayoutDirection() only available since 4.2
        // -> using ViewCompat instead (from Android support library)
        if (ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL) {
            return true;
        }
        return false;
    }
}
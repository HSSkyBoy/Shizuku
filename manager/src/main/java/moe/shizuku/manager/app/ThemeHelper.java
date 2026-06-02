package moe.shizuku.manager.app;

import android.content.Context;
import android.os.Build;

import moe.shizuku.manager.ShizukuSettings;
import moe.shizuku.manager.utils.EnvironmentUtils;
import rikka.core.util.ResourceUtils;

public class ThemeHelper {

    public static final String KEY_BLACK_NIGHT_THEME = "black_night_theme";
    public static final String KEY_USE_SYSTEM_COLOR = "use_system_color";

    public static boolean isBlackNightTheme(Context context) {
        return ShizukuSettings.getPreferences().getBoolean(KEY_BLACK_NIGHT_THEME, EnvironmentUtils.isWatch(context));
    }

    public static boolean isUsingSystemColor() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ShizukuSettings.getPreferences().getBoolean(KEY_USE_SYSTEM_COLOR, true);
    }

    public static String getThemeKey(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append(ShizukuSettings.getNightMode());
        if (isBlackNightTheme(context)) {
            sb.append("_black");
        }
        if (isUsingSystemColor()) {
            sb.append("_dynamic");
        }
        return sb.toString();
    }
}

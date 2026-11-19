package local.hicarlab;

import android.content.Context;

/** Screen ratio scale: changes announced density without changing the wire resolution. */
final class UiScale {
    private static final String KEY = "ui_scale";
    private static final String[] KEYS = {"original","25","50","75","100","125","150","175","200"};
    private static final String[] LABELS = {"原始比例","25%","50%","75%","100%","125%","150%","175%","200%"};
    private static final int[] DPI = {160,40,80,120,160,200,240,280,320};
    private static int index = 0;

    private UiScale() {}

    static void load(Context context) {
        String saved = context.getSharedPreferences("video", 0).getString(KEY, "original");
        index = 0;
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(saved)) index = i;
    }

    static void save(Context context, int selected) {
        index = selected;
        context.getSharedPreferences("video", 0).edit().putString(KEY, KEYS[index]).apply();
    }

    static int index() { return index; }
    static int count() { return KEYS.length; }
    static String option(int i) { return LABELS[i]; }
    static String label() { return LABELS[index]; }
    /** Android density: original/100% => 160dpi; larger value => larger on-screen UI at same pixels. */
    static int dpi() { return DPI[index]; }
}

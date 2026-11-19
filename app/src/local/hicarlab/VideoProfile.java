package local.hicarlab;

import android.content.Context;

/** Receiver display profile announced to the sender. */
final class VideoProfile {
    private static final String KEY = "preset";
    // 比亚迪 DiLink 车机为 16:9 屏（1920×1080 / 1280×720），档位全部按 16:9 适配。
    private static final String[] IDS = {"1080P", "720P", "480P"};
    private static final int[][] SIZES = {{1920,1080},{1280,720},{854,480}};
    private static int index = 0;

    private VideoProfile() {}

    static void load(Context context) {
        String saved = context.getSharedPreferences("video", 0).getString(KEY, "1080P");
        index = 0;
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(saved)) index = i;
    }

    static void save(Context context, int selected) {
        index = selected;
        context.getSharedPreferences("video", 0).edit().putString(KEY, IDS[index]).apply();
    }

    static int index() { return index; }
    static int count() { return IDS.length; }
    static String option(int i) { return IDS[i] + "  " + SIZES[i][0] + "×" + SIZES[i][1]; }
    static String label() { return option(index); }
    static int width() { return SIZES[index][0]; }
    static int height() { return SIZES[index][1]; }
}

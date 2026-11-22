package local.hicarlab;

import android.content.Context;

/** Receiver display profile announced to the sender. */
final class VideoProfile {
    private static final String KEY = "preset";
    private static final String[] IDS = {"2K", "1080P", "720P", "600P", "480P"};
    private static final int[][] SIZES = {{3120,1440},{2340,1080},{1560,720},{1300,600},{1040,480}};
    private static int index = 1;
    private static int screenWidth=13,screenHeight=6;

    private VideoProfile() {}

    static void load(Context context) {
        String saved = context.getSharedPreferences("video", 0).getString(KEY, "1080P");
        index = 1;
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(saved)) index = i;
        refresh(context);
    }

    static void refresh(Context context) {
        android.view.WindowManager manager=(android.view.WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        if(manager==null)return;
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
        manager.getDefaultDisplay().getRealMetrics(metrics);
        if(metrics.widthPixels>0&&metrics.heightPixels>0){screenWidth=metrics.widthPixels;screenHeight=metrics.heightPixels;}
    }

    static int widthFor(int height,int displayWidth,int displayHeight) {
        if(height<1||displayWidth<1||displayHeight<1)throw new IllegalArgumentException("display dimensions");
        long width=((long)height*displayWidth+displayHeight/2)/displayHeight;
        return (int)Math.max(2,Math.min(4096,(width+1)&~1L));
    }

    static int bitrateFor(int width,int height) {
        return (int)Math.max(2000000,Math.min(12000000,(long)width*height*2));
    }

    static void save(Context context, int selected) {
        index = selected;
        refresh(context);
        context.getSharedPreferences("video", 0).edit().putString(KEY, IDS[index]).apply();
    }

    static int index() { return index; }
    static int count() { return IDS.length; }
    static String option(int i) { return IDS[i] + "  " + widthFor(SIZES[i][1],screenWidth,screenHeight) + "×" + SIZES[i][1]; }
    static String label() { return option(index); }
    static int width() { return widthFor(height(),screenWidth,screenHeight); }
    static int height() { return SIZES[index][1]; }
    static int bitrate() { return bitrateFor(width(),height()); }
}

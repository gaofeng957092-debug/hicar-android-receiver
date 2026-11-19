package local.hicarlab;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;

final class UiTheme {
    static int mode(Context c){return c.getSharedPreferences("ui",0).getInt("theme",0);}
    static boolean dark(Context c){int m=mode(c);return m==2 || m==0 && (c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
    static int background(Context c){return M3.surface(c);}
    static int card(Context c){return M3.surface(c);}
    static int text(Context c){return M3.onSurface(c);}
    static int accent(Context c){return M3.primary(c);}
    static String label(Context c){return new String[]{"跟随系统","浅色","深色"}[Math.max(0,Math.min(2,mode(c)))];}
    static void choose(Activity a){
        android.app.AlertDialog d=new AlertDialog.Builder(a).setTitle("外观模式").setSingleChoiceItems(new String[]{"跟随系统","浅色","深色"},mode(a),(dialog,which)->{
            a.getSharedPreferences("ui",0).edit().putInt("theme",which).apply();dialog.dismiss();a.recreate();
        }).setNegativeButton("取消",null).create();
        d.setOnShowListener(x->{M3.dialog(d,a);M3.dialogButton(d.getButton(AlertDialog.BUTTON_POSITIVE),a);M3.dialogButton(d.getButton(AlertDialog.BUTTON_NEGATIVE),a);});
        d.show();
    }
}

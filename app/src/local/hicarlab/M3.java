package local.hicarlab;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;

/** Small framework-only Material 3 token/style helper; no behaviour is changed here. */
final class M3 {
    static final int FILLED=0, TONAL=1, OUTLINED=2, TEXT=3;
    static final int TITLE=0, SUBTITLE=1, BODY=2, LABEL=3, CAPTION=4;
    private M3() {}

    private static final float UI_SCALE = 0.90f;
    static int dp(Context c,float value){return (int)(value*c.getResources().getDisplayMetrics().density*UI_SCALE+.5f);}
    private static boolean dark(Context c){return UiTheme.dark(c);}
    private static int alpha(int color,int a){return Color.argb(a,Color.red(color),Color.green(color),Color.blue(color));}

    static int primary(Context c){return dark(c)?0xff8AB4FF:0xff1565C0;}
    static int onPrimary(Context c){return dark(c)?0xff0A3166:0xffffffff;}
    static int primaryContainer(Context c){return dark(c)?0xff1E4E8C:0xffD7EAFF;}
    static int onPrimaryContainer(Context c){return dark(c)?0xffD7EAFF:0xff0A3166;}
    static int secondaryContainer(Context c){return dark(c)?0xff2A4A73:0xffE3F0FF;}
    static int onSecondaryContainer(Context c){return dark(c)?0xffDCEBFF:0xff0D355E;}
    static int surface(Context c){return dark(c)?0xff101820:0xffFAFCFF;}
    static int surfaceVariant(Context c){return dark(c)?0xff37465A:0xffE8F0FB;}
    static int onSurface(Context c){return dark(c)?0xffE6E0E9:0xff1D1B20;}
    static int onSurfaceVariant(Context c){return dark(c)?0xffCAC4D0:0xff49454F;}
    static int outline(Context c){return dark(c)?0xff938F99:0xff79747E;}
    static int outlineVariant(Context c){return dark(c)?0xff49454F:0xffCAC4D0;}

    private static GradientDrawable shape(Context c,int fill,int stroke,float radiusDp,int strokeDp){
        GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(c,radiusDp));d.setColor(fill);
        if(strokeDp>0)d.setStroke(dp(c,strokeDp),stroke);
        return d;
    }
    static GradientDrawable surfaceShape(Context c,float radiusDp){return shape(c,surface(c),outlineVariant(c),radiusDp,1);}
    static ColorDrawable windowBackground(Context c){return new ColorDrawable(surface(c));}

    static void card(View v,Context c,boolean elevated){
        v.setBackground(shape(c,surface(c),outlineVariant(c),16,1));
        if(elevated)v.setElevation(dp(c,3));
    }
    static void innerCard(View v,Context c){
        v.setBackground(shape(c,surfaceVariant(c),android.graphics.Color.TRANSPARENT,12,0));
    }

    static void button(Button b,Context c,int variant){
        int fill=android.graphics.Color.TRANSPARENT,stroke=android.graphics.Color.TRANSPARENT,text=primary(c),ripple=alpha(primary(c),40),radius=20;
        if(variant==FILLED){fill=primary(c);text=onPrimary(c);ripple=alpha(onPrimary(c),48);}
        else if(variant==TONAL){fill=secondaryContainer(c);text=onSecondaryContainer(c);ripple=alpha(primary(c),36);}
        else if(variant==OUTLINED){stroke=outline(c);ripple=alpha(primary(c),32);}
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple),shape(c,fill,stroke,radius,variant==OUTLINED?1:0),null));
        b.setTextColor(text);b.setAllCaps(false);b.setMinHeight(dp(c,44));
        b.setPadding(dp(c,14),dp(c,8),dp(c,14),dp(c,8));
        b.setLetterSpacing(.01f);b.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
    }
    static void option(Button b,boolean selected,Context c){
        button(b,c,selected?TONAL:OUTLINED);
        b.setTypeface(Typeface.create(Typeface.DEFAULT,selected?Typeface.BOLD:Typeface.NORMAL));
    }
    static void text(TextView v,Context c,int role){
        int size=15,color=onSurface(c);Typeface face=Typeface.DEFAULT;
        if(role==TITLE){size=25;face=Typeface.create(Typeface.DEFAULT,Typeface.BOLD);}
        else if(role==SUBTITLE){size=20;face=Typeface.create(Typeface.DEFAULT,Typeface.BOLD);}
        else if(role==LABEL){size=13;color=onSurfaceVariant(c);}
        else if(role==CAPTION){size=11;color=onSurfaceVariant(c);}
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP,size);v.setTextColor(color);v.setTypeface(face);v.setLetterSpacing(.01f);
    }
    static void editText(EditText e,Context c){
        e.setBackground(shape(c,surface(c),outline(c),12,1));
        e.setTextColor(onSurface(c));e.setHintTextColor(onSurfaceVariant(c));
        e.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));e.setMinHeight(dp(c,48));
    }
    static void switches(Switch s,Context c){
        ColorStateList thumb=new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{primary(c),outline(c)});
        ColorStateList track=new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{primaryContainer(c),surfaceVariant(c)});
        if(s.getThumbDrawable()!=null)s.getThumbDrawable().setTintList(thumb);
        if(s.getTrackDrawable()!=null)s.getTrackDrawable().setTintList(track);
        s.setTextColor(onSurface(c));
    }
    static void dialog(android.app.AlertDialog d,Context c){
        if(d.getWindow()!=null)d.getWindow().setBackgroundDrawable(surfaceShape(c,28));
    }
    static void dialogButton(Button b,Context c){if(b!=null)button(b,c,TEXT);}
    static void styleTree(ViewGroup root,Context c){
        for(int i=0;i<root.getChildCount();i++){
            View v=root.getChildAt(i);
            if(v instanceof Button)button((Button)v,c,TONAL);
            else if(v instanceof EditText)editText((EditText)v,c);
            else if(v instanceof Switch)switches((Switch)v,c);
            else if(v instanceof TextView)((TextView)v).setTextColor(onSurface(c));
            if(v instanceof ViewGroup)styleTree((ViewGroup)v,c);
        }
    }
}

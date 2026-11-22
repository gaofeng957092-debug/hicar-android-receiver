package local.hicarlab;
import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** User-opened video/input test window. The PIN is never included in logs. */
public final class VideoActivity extends ThemeActivity implements TextureView.SurfaceTextureListener {
    private VideoSink sink;
    private InputRouter input;
    private final android.util.SparseIntArray uibcPointer=new android.util.SparseIntArray();
    private TextureView surface;private boolean foreground;
    private LinearLayout root,keys;private TextView title;private Button menu,orientation;
    private boolean fullscreen=true,landscape,detailsExpanded,receiving;
    private final android.os.Handler uiHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private LinearLayout notice;private TextView progress,explanation,micStatus;
    private final Runnable updateStatus=new Runnable(){public void run(){
        ReceiverStatus.Snapshot state=((LabApplication)getApplication()).engine().displayStatus();
        receiving=state.receiving;
        micStatus.setText(((LabApplication)getApplication()).engine().microphoneStatus());updateInfoVisibility();
        progress.setText(state.title);explanation.setText(state.detail);notice.setVisibility(state.receiving?View.GONE:View.VISIBLE);
        uiHandler.postDelayed(this,700);
    }};
    private void reconnect(){
        resetTouches();((LabApplication)getApplication()).engine().stop(true);
        startActivity(new android.content.Intent(this,HomeActivity.class).putExtra("retry",true).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();
    }
    private int dp(int value){return M3.dp(this,value);}
    private Button key(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setMinWidth(0);b.setMinimumWidth(0);M3.button(b,this,M3.TONAL);
        b.setOnClickListener(v->{resetTouches();action.run();});
        keys.addView(b,new LinearLayout.LayoutParams(0,-2,1));return b;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sink=((LabApplication)getApplication()).video();input=((LabApplication)getApplication()).input();
        if(state!=null){fullscreen=state.getBoolean("fullscreen");landscape=state.getBoolean("landscape");detailsExpanded=state.getBoolean("detailsExpanded");}
        receiving=((LabApplication)getApplication()).engine().displayStatus().receiving;
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff000000);
        title=new TextView(this);title.setText("HiCar  ·  连接码："+getSharedPreferences("identity",0).getString("pin","未就绪"));
        title.setPadding(dp(16),dp(12),dp(90),dp(4));
        micStatus=new TextView(this);micStatus.setPadding(dp(16),0,dp(90),dp(4));
        surface=new TextureView(this){@Override protected void onMeasure(int widthSpec,int heightSpec){
            int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);
            int vw=VideoProfile.width(),vh=VideoProfile.height();
            // Fullscreen preserves all content and fills the available screen.
            // Touch coordinates use the same displayed width/height below.
            if(!fullscreen&&vw>0&&vh>0){if(w*vh>h*vw)w=h*vw/vh;else h=w*vh/vw;}
            setMeasuredDimension(w,h);
        }};
        surface.setOnTouchListener((v,event)->touch(v,event));surface.setSurfaceTextureListener(this);
        final FrameLayout area=new FrameLayout(this);area.setBackgroundColor(0xff000000);area.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
        info.addView(title);info.addView(micStatus);area.addView(info,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        notice=new LinearLayout(this);notice.setOrientation(LinearLayout.VERTICAL);notice.setGravity(Gravity.CENTER);notice.setPadding(dp(20),dp(20),dp(20),dp(20));notice.setClickable(true);M3.surfaceShape(this,24);
        progress=new TextView(this);progress.setGravity(Gravity.CENTER);notice.addView(progress);
        explanation=new TextView(this);explanation.setGravity(Gravity.CENTER);explanation.setPadding(0,dp(10),0,dp(10));notice.addView(explanation);
        Button retry=new Button(this);retry.setText("重新连接");retry.setOnClickListener(v->reconnect());notice.addView(retry);
        FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);np.leftMargin=dp(20);np.rightMargin=dp(20);area.addView(notice,np);
        menu=new Button(this);menu.setText("菜单");menu.setMinWidth(0);menu.setMinimumWidth(0);menu.setAlpha(0.55f);M3.button(menu,this,M3.TONAL);
        FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(dp(76),dp(48),Gravity.TOP|Gravity.START);mp.topMargin=dp(6);area.addView(menu,mp);
        area.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)menu.getLayoutParams();
            int maxX=Math.max(0,r-l-menu.getWidth()-dp(6)),maxY=Math.max(0,b-t-menu.getHeight()-dp(6));
            boolean atRight=or==ol||p.leftMargin>=or-ol-menu.getWidth()-dp(12);
            int x=atRight?maxX:Math.min(p.leftMargin,maxX),y=Math.min(p.topMargin,maxY);
            if(p.leftMargin!=x||p.topMargin!=y){p.leftMargin=x;p.topMargin=y;menu.setLayoutParams(p);}
        });
        final float[] down=new float[2];final int[] origin=new int[2];final boolean[] dragged={false};
        menu.setOnTouchListener((v,event)->{
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)menu.getLayoutParams();
            if(event.getAction()==MotionEvent.ACTION_DOWN){down[0]=event.getRawX();down[1]=event.getRawY();origin[0]=p.leftMargin;origin[1]=p.topMargin;dragged[0]=false;return true;}
            if(event.getAction()==MotionEvent.ACTION_MOVE){
                float dx=event.getRawX()-down[0],dy=event.getRawY()-down[1];
                if(Math.abs(dx)+Math.abs(dy)>8)dragged[0]=true;
                if(dragged[0]){
                    p.leftMargin=(int)Math.max(0,Math.min(area.getWidth()-menu.getWidth(),origin[0]+dx));
                    p.topMargin=(int)Math.max(0,Math.min(area.getHeight()-menu.getHeight(),origin[1]+dy));
                    menu.setLayoutParams(p);
                }
                return true;
            }
            if(event.getAction()==MotionEvent.ACTION_UP){if(!dragged[0])showMenu();return true;}
            return true;
        });
        root.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        keys=new LinearLayout(this);keys.setBackgroundColor(M3.surfaceVariant(this));
        key("返回",()->{if(!input.key(4))Toast.makeText(this,"输入通道尚未就绪",Toast.LENGTH_SHORT).show();});
        key("主页",()->{if(!input.key(3))Toast.makeText(this,"输入通道尚未就绪",Toast.LENGTH_SHORT).show();});
        orientation=key("横屏",()->{landscape=!landscape;applyOrientation();});
        key("全屏",()->setFullscreen(true));
        key("重连",this::reconnect);
        key("停止",()->{((LabApplication)getApplication()).engine().stop(true);finish();});root.addView(keys);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(fullscreen){v.setPadding(0,0,0,0);return insets;}
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets edge=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(edge.left,edge.top,edge.right,edge.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        M3.text(title,this,M3.SUBTITLE);M3.text(micStatus,this,M3.CAPTION);M3.text(progress,this,M3.TITLE);M3.text(explanation,this,M3.BODY);M3.button(retry,this,M3.TONAL);
        notice.setBackground(M3.surfaceShape(this,24));
        menu.bringToFront();
        setContentView(root);applyOrientation();setFullscreen(fullscreen);
    }
    private void applyOrientation(){
        orientation.setText(landscape?"自动旋转":"横屏");
        setRequestedOrientation(landscape?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
    private void setFullscreen(boolean enabled){
        resetTouches();fullscreen=enabled;
        updateInfoVisibility();keys.setVisibility(enabled?View.GONE:View.VISIBLE);menu.setVisibility(View.VISIBLE);
        surface.requestLayout();
        if(android.os.Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            WindowManager.LayoutParams params=getWindow().getAttributes();
            params.layoutInDisplayCutoutMode=enabled?WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS:WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            getWindow().setAttributes(params);
            WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(enabled)c.hide(WindowInsets.Type.systemBars());else c.show(WindowInsets.Type.systemBars());}
        }else getWindow().getDecorView().setSystemUiVisibility(enabled?View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION:View.SYSTEM_UI_FLAG_VISIBLE);
        root.requestApplyInsets();
    }
    private void updateInfoVisibility(){
        // The code remains available before connection; routine playback is uncluttered.
        title.setVisibility(detailsExpanded||!receiving?View.VISIBLE:View.GONE);
        micStatus.setVisibility(detailsExpanded&&MicrophoneSender.enabled(this)?View.VISIBLE:View.GONE);
    }
    private void showMenu(){
        resetTouches();
        PopupMenu popup=new PopupMenu(this,menu);
        popup.getMenu().add(0,1,0,detailsExpanded?"隐藏连接信息":"显示连接信息");
        popup.getMenu().add(0,2,1,fullscreen?"退出全屏":"自适应全屏");
        popup.setOnMenuItemClickListener(item->{
            if(item.getItemId()==1){detailsExpanded=!detailsExpanded;updateInfoVisibility();return true;}
            if(item.getItemId()==2){setFullscreen(!fullscreen);return true;}
            return false;
        });popup.show();
    }
    @Override public void onBackPressed(){if(fullscreen)setFullscreen(false);else super.onBackPressed();}
    @Override public void onConfigurationChanged(android.content.res.Configuration value){super.onConfigurationChanged(value);resetTouches();root.post(()->setFullscreen(fullscreen));}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("fullscreen",fullscreen);state.putBoolean("landscape",landscape);state.putBoolean("detailsExpanded",detailsExpanded);super.onSaveInstanceState(state);}
    private int assignPointer(int id){
        int known=uibcPointer.get(id,-1);if(known>=0)return known;
        for(int p=0;p<5;p++)if(uibcPointer.indexOfValue(p)<0){uibcPointer.put(id,p);return p;}
        return -1;
    }
    private void resetTouches(){input.cancelAll();uibcPointer.clear();}
    private boolean touch(View view,MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_CANCEL){resetTouches();return true;}
        if(action==MotionEvent.ACTION_DOWN){
            resetTouches();
            if(!sink.hasFrame()||!input.ready())return false;
        }else if(uibcPointer.size()==0)return false;
        if(action!=MotionEvent.ACTION_DOWN&&action!=MotionEvent.ACTION_POINTER_DOWN&&action!=MotionEvent.ACTION_MOVE&&action!=MotionEvent.ACTION_UP&&action!=MotionEvent.ACTION_POINTER_UP)return true;
        view.getParent().requestDisallowInterceptTouchEvent(true);
        int changedAndroid=event.getActionIndex();
        int newId=event.getPointerId(changedAndroid);
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN)assignPointer(newId);
        int count=uibcPointer.size();int[] ids=new int[count],xs=new int[count],ys=new int[count];
        int next=0,changed=0;
        for(int i=0;i<event.getPointerCount();i++){
            int id=event.getPointerId(i),p=uibcPointer.get(id,-1);if(p<0)continue;
            int[] pos=InputRouter.map(event.getX(i),event.getY(i),view.getWidth(),view.getHeight(),action!=MotionEvent.ACTION_DOWN);
            if(pos==null){resetTouches();return true;}
            ids[next]=p;xs[next]=pos[0];ys[next]=pos[1];if(i==changedAndroid)changed=next;next++;
        }
        if(next!=count){resetTouches();return true;}
        boolean ignored=action==MotionEvent.ACTION_POINTER_DOWN&&uibcPointer.get(newId,-1)<0
            ||action==MotionEvent.ACTION_POINTER_UP&&uibcPointer.get(newId,-1)<0;
        if(!ignored){
            int wireAction=action==MotionEvent.ACTION_MOVE?2:action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP?1:0;
            if(!input.touchFrame(wireAction,ids,xs,ys,changed)){resetTouches();return true;}
            if(count>1&&wireAction!=2)DiagnosticLog.add(this,"多指输入边沿：触点 "+count+"，动作 "+wireAction+"，变化索引 "+changed+"；不记录坐标。");
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)uibcPointer.delete(newId);
        if(action==MotionEvent.ACTION_UP){uibcPointer.clear();view.performClick();view.getParent().requestDisallowInterceptTouchEvent(false);}
        return true;
    }
    public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture value,int width,int height){
        try{sink.attach(surface,value);if(!foreground)sink.hide(surface);}
        catch(RuntimeException e){DiagnosticLog.add(this,"接收窗口附着失败："+e.getClass().getSimpleName());((LabApplication)getApplication()).engine().stop(true);((LabApplication)getApplication()).engine().reportProblem("画面窗口未能恢复，请重新连接。");}
    }
    public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture value,int width,int height){}
    public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture value){}
    public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture value){resetTouches();return sink.detach(surface,value);}
    @Override protected void onResume(){super.onResume();foreground=true;if(surface!=null)sink.show(surface);if(root!=null)root.post(()->setFullscreen(fullscreen));}
    @Override protected void onPause(){foreground=false;resetTouches();if(surface!=null)sink.hide(surface);super.onPause();}
    @Override protected void onStart(){super.onStart();uiHandler.removeCallbacks(updateStatus);uiHandler.post(updateStatus);}
    @Override protected void onStop(){uiHandler.removeCallbacks(updateStatus);super.onStop();}
    @Override protected void onDestroy(){uiHandler.removeCallbacksAndMessages(null);if(surface!=null)sink.hide(surface);super.onDestroy();}
}

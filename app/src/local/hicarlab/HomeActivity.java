package local.hicarlab;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Daily receiver controls. Protocol experiments remain in MainActivity. */
public final class HomeActivity extends ThemeActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,detail,micStatus;
    private Switch micSwitch;
    private Button start,stop,retry,resolution,scale;
    private LinearLayout resolutionOptions,scaleOptions;
    private boolean pending;
    private static final int PERMISSIONS=60,ENABLE=61,DISCOVER=62,MICROPHONE=63,NOTIFICATIONS=64;
    private ReceiverEngine engine(){return ((LabApplication)getApplication()).engine();}
    private int dp(int value){return M3.dp(this,value);}
    private final Runnable update=new Runnable(){public void run(){
        if(status==null)return;
        boolean active=engine().isActive();
        ReceiverStatus.Snapshot state=engine().displayStatus();
        micStatus.setText(engine().microphoneStatus());
        status.setText(state.title);detail.setText(state.detail);retry.setVisibility(active||state.problem?View.VISIBLE:View.GONE);
        start.setText(active?"返回接收画面":"开始连接");
        start.setEnabled(!pending);stop.setEnabled(active);handler.postDelayed(this,700);
    }};
    private interface Choice { void pick(int index); }
    private LinearLayout card(LinearLayout root,int pad,int topMargin){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(pad),dp(pad),dp(pad),dp(pad));M3.card(c,this,true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(topMargin);root.addView(c,p);return c;
    }
    private LinearLayout row(LinearLayout root){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(7);root.addView(r,p);return r;
    }
    private Button cell(LinearLayout row,String text,int variant,Runnable action){
        Button b=new Button(this);b.setText(text);M3.button(b,this,variant);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(6),dp(8),dp(6),dp(8));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);
        if(row.getChildCount()>0)p.leftMargin=dp(6);
        row.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    private void sizeButtons(android.view.ViewGroup group){
        for(int i=0;i<group.getChildCount();i++){
            android.view.View v=group.getChildAt(i);
            if(v instanceof Button){
                Button b=(Button)v;String t=b.getText().toString();float size=13f;
                if(t.length()>10)size=11.5f;
                if(t.length()>14)size=10.5f;
                b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,size);
                b.setMinWidth(0);b.setMinimumWidth(0);
            }else if(v instanceof android.view.ViewGroup)sizeButtons((android.view.ViewGroup)v);
        }
    }
    private void markChoices(LinearLayout list,int selected){
        for(int i=0;i<list.getChildCount();i++)if(list.getChildAt(i) instanceof Button)M3.option((Button)list.getChildAt(i),i==selected,this);
    }
    private LinearLayout choices(LinearLayout root,String[] labels,int selected,Choice choice){
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12),dp(8),dp(12),dp(8));M3.card(list,this,false);list.setVisibility(View.GONE);
        for(int i=0;i<labels.length;i++){
            Button b=new Button(this);b.setText(labels[i]);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(6);
            final int index=i;b.setOnClickListener(v->choice.pick(index));list.addView(b,p);
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(4);root.addView(list,lp);
        markChoices(list,selected);return list;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(state!=null)pending=state.getBoolean("pending");
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(M3.surface(this));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(32),dp(16),dp(16));root.setBackgroundColor(M3.surface(this));scroll.addView(root);

        // 标题与版本号合并为一行，减少占用。
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(android.view.Gravity.CENTER_VERTICAL);root.addView(head);
        TextView title=new TextView(this);title.setText("HiCar 接收端");
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);head.addView(title,tp);
        TextView version=new TextView(this);version.setText("实验版 0.11.1");head.addView(version);

        // 状态卡片（最常用信息，置顶）。
        LinearLayout statusCard=card(root,dp(10),dp(2));
        status=new TextView(this);statusCard.addView(status);
        detail=new TextView(this);detail.setPadding(0,dp(2),0,0);statusCard.addView(detail);

        // 主操作区：开始/停止/重新连接，两列网格，一屏可见。
        LinearLayout actions=row(root);
        start=cell(actions,"开始连接",M3.FILLED,()->{if(engine().isActive())openVideo();else{pending=true;continueStart();}});
        stop=cell(actions,"停止连接",M3.OUTLINED,()->{pending=false;engine().stop(true);status.setText("已停止");});
        retry=cell(actions,"重新连接",M3.TONAL,()->{engine().stop(true);pending=true;continueStart();});
        cell(actions,"试运行自动",M3.TONAL,()->{
            if(!AutoStart.enabled(this)){Toast.makeText(this,"请先开启“开机自动接收”",Toast.LENGTH_LONG).show();return;}
            String problem=AutoStart.problem(this);if(problem!=null){Toast.makeText(this,problem,Toast.LENGTH_LONG).show();return;}
            if(engine().isActive()){Toast.makeText(this,"请先停止当前连接，再试运行自动接收",Toast.LENGTH_LONG).show();return;}
            try{startForegroundService(new Intent(this,ReceiverService.class).setAction("AUTO_START"));Toast.makeText(this,"正在试运行：等待手机连接，点击接收通知打开画面；本轮不启用麦克风",Toast.LENGTH_LONG).show();}
            catch(RuntimeException e){Toast.makeText(this,"系统未允许自动接收，请检查后台运行设置",Toast.LENGTH_LONG).show();}
        });

        // 分辨率与比例：两列，选项列表按需展开。
        String[] resolutions=new String[VideoProfile.count()];for(int i=0;i<resolutions.length;i++)resolutions[i]=VideoProfile.option(i);
        resolution=cell(row(root),"分辨率："+VideoProfile.label(),M3.TONAL,()->{
            if(engine().isActive()){Toast.makeText(this,"请先停止连接，再切换分辨率",Toast.LENGTH_LONG).show();return;}
            boolean open=resolutionOptions.getVisibility()!=View.VISIBLE;
            resolutionOptions.setVisibility(open?View.VISIBLE:View.GONE);if(open)scaleOptions.setVisibility(View.GONE);
        });
        resolutionOptions=choices(root,resolutions,VideoProfile.index(),index->{VideoProfile.save(this,index);resolution.setText("分辨率："+VideoProfile.label());markChoices(resolutionOptions,index);resolutionOptions.setVisibility(View.GONE);Toast.makeText(this,"已选择，下次连接生效",Toast.LENGTH_SHORT).show();});
        String[] scales=new String[UiScale.count()];for(int i=0;i<scales.length;i++)scales[i]=UiScale.option(i);
        scale=cell(row(root),"比例："+UiScale.label(),M3.TONAL,()->{
            boolean open=scaleOptions.getVisibility()!=View.VISIBLE;
            scaleOptions.setVisibility(open?View.VISIBLE:View.GONE);if(open)resolutionOptions.setVisibility(View.GONE);
        });
        scaleOptions=choices(root,scales,UiScale.index(),index->{UiScale.save(this,index);scale.setText("比例："+UiScale.label());markChoices(scaleOptions,index);scaleOptions.setVisibility(View.GONE);Toast.makeText(this,"已选择，下次连接生效",Toast.LENGTH_SHORT).show();});

        // 开关区：麦克风与开机自启，说明文字精简并缩小。
        LinearLayout toggles=card(root,dp(10),dp(2));
        micSwitch=new Switch(this);micSwitch.setText("麦克风语音实验");micSwitch.setChecked(MicrophoneSender.enabled(this));toggles.addView(micSwitch);
        micStatus=new TextView(this);micStatus.setPadding(0,dp(2),0,dp(6));micStatus.setText("仅在手机请求语音时采集；关闭不影响画面与声音");toggles.addView(micStatus);
        micSwitch.setOnCheckedChangeListener((button,checked)->{
            if(checked&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){micSwitch.setChecked(false);requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE);return;}
            getSharedPreferences("ui",0).edit().putBoolean("microphone",checked).apply();
            if(!checked)engine().disableMicrophone();
            else if(engine().isActive())Toast.makeText(this,"重新连接后启用；仅在手机请求语音时采集",Toast.LENGTH_LONG).show();
        });
        Switch autoStart=new Switch(this);autoStart.setText("开机自动接收");autoStart.setChecked(AutoStart.enabled(this));toggles.addView(autoStart);
        TextView autoHelp=new TextView(this);autoHelp.setPadding(0,dp(2),0,0);autoHelp.setText("开机自动等待已配对手机；需系统允许自启动与后台运行。通知点开画面，语音需重连。");toggles.addView(autoHelp);
        autoStart.setOnCheckedChangeListener((button,checked)->{
            if(checked){String problem=AutoStart.problem(this);if(problem!=null){autoStart.setChecked(false);Toast.makeText(this,problem,Toast.LENGTH_LONG).show();return;}}
            getSharedPreferences("ui",0).edit().putBoolean("auto_start",checked).apply();
            if(checked&&Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},NOTIFICATIONS);
            Toast.makeText(this,checked?"已启用，下次开机自动等待连接；请在系统中允许自启动":"已关闭开机自动接收；当前连接不受影响",Toast.LENGTH_LONG).show();
        });

        // 其他设置：两列网格。
        LinearLayout settings=row(root);
        cell(settings,"外观",M3.TONAL,()->UiTheme.choose(this));
        cell(settings,"设置与诊断",M3.TONAL,()->startActivity(new Intent(this,MainActivity.class)));
        cell(settings,"后台设置",M3.TONAL,()->{try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));}catch(RuntimeException e){Toast.makeText(this,"请在系统设置中打开本应用详情",Toast.LENGTH_LONG).show();}});

        TextView note=new TextView(this);note.setText("开启手机蓝牙和 WLAN · 首次按提示输入画面中的六位码 · 请停车后操作");
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(8);root.addView(note,np);

        M3.styleTree(root,this);
        M3.text(title,this,M3.TITLE);M3.text(version,this,M3.CAPTION);M3.text(status,this,M3.SUBTITLE);status.setTextColor(M3.primary(this));M3.text(detail,this,M3.BODY);
        M3.text(micStatus,this,M3.CAPTION);M3.text(autoHelp,this,M3.CAPTION);M3.text(note,this,M3.CAPTION);
        M3.button(start,this,M3.FILLED);M3.button(stop,this,M3.OUTLINED);M3.button(retry,this,M3.TONAL);
        sizeButtons(root);
        markChoices(resolutionOptions,VideoProfile.index());markChoices(scaleOptions,UiScale.index());
        setContentView(scroll);
        if(getIntent().getBooleanExtra("retry",false)){getIntent().removeExtra("retry");handler.post(()->{pending=true;continueStart();});}
    }
    private Button button(LinearLayout root,String title,Runnable action){
        Button b=new Button(this);b.setText(title);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);root.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    private void message(String value){pending=false;engine().reportProblem(value);Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void continueStart(){
        if(!pending)return;
        if(engine().isActive()){pending=false;openVideo();return;}
        VideoProfile.refresh(this);resolution.setText("分辨率："+VideoProfile.label());
        List<String> missing=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=31)for(String p:new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)missing.add(p);
        String wifi=Build.VERSION.SDK_INT>=33?"android.permission.NEARBY_WIFI_DEVICES":Manifest.permission.ACCESS_FINE_LOCATION;
        if(checkSelfPermission(wifi)!=PackageManager.PERMISSION_GRANTED)missing.add(wifi);
        if(!missing.isEmpty()){requestPermissions(missing.toArray(new String[0]),PERMISSIONS);return;}
        BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null){message("这台设备没有可用蓝牙适配器");return;}
        try{
            if(!adapter.isEnabled()){startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),ENABLE);return;}
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,120),DISCOVER);
        }catch(RuntimeException e){message("系统无法打开蓝牙连接请求，请到设置与诊断查看");}
    }
    private void openVideo(){startActivity(new Intent(this,VideoActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==NOTIFICATIONS){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"通知未开启，自动接收时需从应用入口打开画面",Toast.LENGTH_LONG).show();return;}
        if(request==MICROPHONE){
            boolean granted=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
            getSharedPreferences("ui",0).edit().putBoolean("microphone",granted).apply();micSwitch.setChecked(granted);
            Toast.makeText(this,granted?"已启用语音实验；连接后在 HiCar 中启动语音":"未启用麦克风，画面和播放不受影响",Toast.LENGTH_LONG).show();return;
        }
        if(request==PERMISSIONS){if(results.length==0){message("已取消权限申请");return;}for(int result:results)if(result!=PackageManager.PERMISSION_GRANTED){message("需要蓝牙及附近设备权限才能开始连接");return;}continueStart();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(!pending)return;
        if(request==ENABLE){if(result==RESULT_OK)continueStart();else message("蓝牙尚未开启");}
        if(request==DISCOVER){
            pending=false;if(result<=0){message("已取消开始连接");return;}
            String model=getSharedPreferences("identity",0).getString("wire_model","00048000");
            if(model==null || !model.matches("[0-9A-Fa-f]{8}")){message("接收设置无效，请到设置与诊断检查");return;}
            try{startForegroundService(new Intent(this,ReceiverService.class).putExtra("model",model).putExtra("network",true).putExtra("video",true));openVideo();}
            catch(RuntimeException e){message("接收服务启动失败，请查看诊断页");}
        }
    }
    @Override protected void onStart(){super.onStart();handler.removeCallbacks(update);handler.post(update);}
    @Override protected void onStop(){handler.removeCallbacks(update);super.onStop();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("pending",pending);super.onSaveInstanceState(state);}
}

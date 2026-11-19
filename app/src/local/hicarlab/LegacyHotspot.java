package local.hicarlab;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Android 8/9 fallback using public startLocalOnlyHotspot (API 26+). */
final class LegacyHotspot implements AutoCloseable {
    private final Context context;
    private final P2pHotspot.Events events;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final WifiManager manager;
    private final Set<String> oldAddresses=new HashSet<>();
    private final Runnable poll=this::inspect;
    private volatile boolean closed,started;
    private int attempts;
    private String ssid,password;
    private int hotspotChannel;
    private WifiManager.LocalOnlyHotspotReservation reservation;

    LegacyHotspot(Context c,P2pHotspot.Events e){
        context=c.getApplicationContext();events=e;
        manager=(WifiManager)context.getSystemService(Context.WIFI_SERVICE);
    }

    private static Set<String> addresses(){
        Set<String> result=new HashSet<>();
        try{
            Enumeration<NetworkInterface> networks=NetworkInterface.getNetworkInterfaces();
            while(networks.hasMoreElements())for(InterfaceAddress a:networks.nextElement().getInterfaceAddresses())
                if(a.getAddress() instanceof Inet4Address&&!a.getAddress().isLoopbackAddress()&&a.getNetworkPrefixLength()==24)result.add(a.getAddress().getHostAddress());
        }catch(Exception ignored){}
        return result;
    }

    void start(){
        if(manager==null){events.failed("系统未提供 Wi-Fi 服务，无法创建热点");return;}
        try{
            oldAddresses.addAll(addresses());
            WifiManager.LocalOnlyHotspotCallback callback=new WifiManager.LocalOnlyHotspotCallback(){
                @Override public void onStarted(WifiManager.LocalOnlyHotspotReservation r){
                    if(closed){r.close();return;}
                    reservation=r;started=true;
                    try{
                        Object cfg=null;
                        try{cfg=r.getClass().getMethod("getSoftApConfiguration").invoke(r);}
                        catch(Throwable noSoftAp){
                            try{cfg=r.getClass().getMethod("getWifiConfiguration").invoke(r);}catch(Throwable ignored){}
                        }
                        if(cfg!=null){
                            Object s=null,p=null;
                            try{s=cfg.getClass().getMethod("getSsid").invoke(cfg);}catch(Throwable ignored){}
                            if(s==null)try{s=cfg.getClass().getField("SSID").get(cfg);}catch(Throwable ignored){}
                            try{p=cfg.getClass().getMethod("getPassphrase").invoke(cfg);}catch(Throwable ignored){}
                            if(p==null)try{p=cfg.getClass().getField("preSharedKey").get(cfg);}catch(Throwable ignored){}
                            if(s!=null)ssid=s.toString().replace("\"","");
                            if(p!=null)password=p.toString().replace("\"","");
                            // 热点真实信道：SoftApConfiguration#getChannel() / WifiConfiguration.apChannel
                            try{Object c=cfg.getClass().getMethod("getChannel").invoke(cfg);if(c instanceof Integer)hotspotChannel=(Integer)c;}
                            catch(Throwable ignored){try{Object c=cfg.getClass().getField("apChannel").get(cfg);if(c instanceof Integer)hotspotChannel=(Integer)c;}catch(Throwable ignored2){}}
                        }
                    }catch(Throwable ignored){}
                    events.log("Android 8/9 本地热点已启动；SSID/密码由系统生成。");
                    handler.postDelayed(poll,500);
                }
                @Override public void onFailed(int reason){if(!closed)events.failed("本地热点启动失败，系统代码 "+reason);}
                @Override public void onStopped(){if(!closed)events.failed("本地热点被系统停止");}
            };
            // startLocalOnlyHotspot 从 API 26 起公开，传 null 配置让系统用默认参数。
            try{
                manager.getClass().getMethod("startLocalOnlyHotspot",WifiManager.LocalOnlyHotspotCallback.class,Handler.class)
                    .invoke(manager,callback,handler);
            }catch(Throwable oldSig){
                // 部分 API26 早期实现签名带 (WifiConfiguration, callback, handler)
                manager.getClass().getMethod("startLocalOnlyHotspot",WifiConfiguration.class,WifiManager.LocalOnlyHotspotCallback.class,Handler.class)
                    .invoke(manager,null,callback,handler);
            }
        }catch(Throwable t){events.failed("本地热点接口不可用："+t.getClass().getSimpleName());}
    }

    private void inspect(){
        if(closed||!started)return;
        Set<String> added=addresses();added.removeAll(oldAddresses);
        if(added.size()!=1){
            if(++attempts<40){handler.postDelayed(poll,500);return;}
            events.failed("无法唯一确认热点 IPv4 地址");return;
        }
        String owner=added.iterator().next();
        int dot=owner.lastIndexOf('.');
        String client=owner.substring(0,dot+1)+(owner.endsWith(".2")?"3":"2");
        if(ssid==null)ssid="AndroidShare";
        if(password==null||password.length()<8)password="00000000";
        // HiCar 网络协议只接受 9 个固定 5GHz 频点（ch36/40/44/48/149/153/157/161/165），2.4GHz 不在协议内。
        // 之前用假频率 5200 声明：手机拿到凭据后按 5GHz 去扫，而热点真实在 2.4GHz，始终连不上。
        // 改为按真实信道声明；若不是协议允许的 5GHz，明确报错，不再用假频率让手机反复重试。
        int frequency=0;
        if(hotspotChannel>=36&&hotspotChannel<=64)frequency=5180+(hotspotChannel-36)*5;
        else if(hotspotChannel>=149&&hotspotChannel<=165)frequency=5745+(hotspotChannel-149)*5;
        if(NetworkResource.frequencyMask(frequency)==0){
            events.failed("本设备本地热点运行在 2.4GHz 或系统未报告 5GHz 信道；HiCar 网络协议要求 5GHz，此设备无法作为投屏热点。请改用支持 5GHz 热点的设备（Android 10+ 会自动使用 5GHz）。");
            return;
        }
        NetworkResource.Offer offer=NetworkResource.Offer.ssidOnly(ssid,password,owner,client,frequency,4005);
        events.log("传统热点已就绪：SSID="+ssid+"、本端="+owner+"、对端="+client+"、真实频率="+frequency+"MHz（仅 SSID 凭据）。");
        events.ready(offer);
    }

    @Override public void close(){
        closed=true;handler.removeCallbacks(poll);
        if(reservation!=null){try{reservation.close();}catch(Throwable ignored){}reservation=null;events.log("本轮本地热点已关闭。");}
    }
}

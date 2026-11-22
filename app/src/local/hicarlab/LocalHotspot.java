package local.hicarlab;

import android.content.Context;
import android.net.wifi.*;
import android.os.*;
import android.util.SparseIntArray;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Executor;

/** Public AP creation with experimental SystemApi runtime-info callback. */
final class LocalHotspot implements AutoCloseable {
    private final Context context;
    private final P2pHotspot.Events events;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WifiManager.LocalOnlyHotspotReservation reservation;
    private final Set<String> oldAddresses=new HashSet<>();
    private volatile boolean closed;
    private int attempts;
    private LocalApInfo watch;
    private String scannedBssid,requestedBssid;private int actualFrequency;
    private boolean delivered;
    LocalHotspot(Context c,P2pHotspot.Events e){context=c.getApplicationContext();events=e;}
    private static Set<String> addresses()throws Exception {
        Set<String> result=new HashSet<>();Enumeration<NetworkInterface> networks=NetworkInterface.getNetworkInterfaces();
        while(networks.hasMoreElements())for(InterfaceAddress a:networks.nextElement().getInterfaceAddresses())
            if(a.getAddress() instanceof Inet4Address&&!a.getAddress().isLoopbackAddress()&&a.getNetworkPrefixLength()==24)result.add(a.getAddress().getHostAddress());
        return result;
    }
    private String rootBssid(String owner){
        String script="for d in /sys/class/net/*; do dev=${d##*/}; "
            +"case $dev in lo|vgate*|wlan0|rmnet*|ccmni*|dummy*) continue;; esac; "
            +"ip -4 addr show dev $dev 2>/dev/null | grep -q /24 || continue; "
            +"a=$(cat $d/address 2>/dev/null); case $a in *:*:*) echo $a; break;; esac; done";
        try{
            java.lang.Process p=Runtime.getRuntime().exec(new String[]{"/system/bin/su","-c",script});
            java.io.BufferedReader r=new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line;String found=null;
            while((line=r.readLine())!=null){line=line.trim().toUpperCase(Locale.ROOT);
                if(line.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}"))found=line;}
            p.waitFor();
            if(found!=null&&(Integer.parseInt(found.substring(0,2),16)&1)==0
                    &&!found.equals("00:00:00:00:00:00")&&!found.equals("02:00:00:00:00:00")){
                events.log("[root BSSID] App 已通过 su 读取到真实 BSSID（值不写日志）。");return found;
            }
            events.log("[root BSSID] su 已执行但未取得可用地址。");
        }catch(Exception e){events.log("[root BSSID] su 执行失败："+e.getClass().getSimpleName()+" "+e.getMessage());}
        return null;
    }
    void start(){
        if(Build.VERSION.SDK_INT<33){events.failed("本轮本地热点兼容接口需要 Android 13 以上");return;}
        try{
            oldAddresses.addAll(addresses());
            byte[] random=new byte[12];new SecureRandom().nextBytes(random);StringBuilder pass=new StringBuilder();for(byte b:random)pass.append(String.format(Locale.ROOT,"%02x",b&255));
            Class<?> configClass=Class.forName("android.net.wifi.SoftApConfiguration"),builderClass=Class.forName("android.net.wifi.SoftApConfiguration$Builder");
            Object builder=builderClass.getConstructor().newInstance();
            builderClass.getMethod("setSsid",String.class).invoke(builder,"HiCarLab-"+pass.substring(0,8));
            builderClass.getMethod("setPassphrase",String.class,int.class).invoke(builder,pass.toString(),1);
            SparseIntArray channels=new SparseIntArray();channels.put(2,149);
            builderClass.getMethod("setChannels",SparseIntArray.class).invoke(builder,channels);
            Object config=builderClass.getMethod("build").invoke(builder);
            if(Build.VERSION.SDK_INT<36){config=CustomApConfiguration.create("HiCarLab-"+pass.substring(0,8),pass.toString());Object requested=config.getClass().getMethod("getBssid").invoke(config);requestedBssid=requested==null?null:requested.toString();events.log("请求应用自有的本地随机热点地址；是否保留由系统启动回调确认，未使用外部地址。");}
            WifiManager manager=(WifiManager)context.getSystemService(Context.WIFI_SERVICE);
            events.log(Build.VERSION.SDK_INT>=36?"请求公开接口创建临时本地热点，指定 5 GHz 信道 149；不记录名称或密码。":"尝试本地热点 SystemApi 兼容接口，指定 5 GHz 信道 149；普通调用、不修改权限或系统限制。");
            WifiManager.LocalOnlyHotspotCallback callback=new WifiManager.LocalOnlyHotspotCallback(){
                @Override public void onStarted(WifiManager.LocalOnlyHotspotReservation r){if(closed){r.close();return;}reservation=r;inspect();}
                @Override public void onFailed(int reason){if(!closed)events.failed("本地热点启动失败，系统代码 "+reason);}
                @Override public void onStopped(){if(!closed)events.failed("本地热点被系统停止");}
            };
            Executor executor=command->handler.post(command);
            String method=Build.VERSION.SDK_INT>=36?"startLocalOnlyHotspotWithConfiguration":"startLocalOnlyHotspot";
            WifiManager.class.getMethod(method,configClass,Executor.class,WifiManager.LocalOnlyHotspotCallback.class).invoke(manager,config,executor,callback);
        }catch(Exception e){events.failed("公开本地热点接口失败："+e.getClass().getSimpleName());}
    }
    private void inspect(){
        if(closed||delivered)return;
        try{
            Object config=reservation.getClass().getMethod("getSoftApConfiguration").invoke(reservation);
            Object address=config.getClass().getMethod("getBssid").invoke(config);
            // getBssid() 在 MAC 随机化的热点上返回 null，但真实地址常藏在 config 的其他字段里。
            // 反射扫描所有字段，找第一个合法的本地管理 MAC（真机 dumpsys 能看到这类值）。
            if(address==null||address.toString().equals("02:00:00:00:00:00")){
                java.lang.reflect.Field[] fs=config.getClass().getDeclaredFields();
                for(java.lang.reflect.Field f:fs){
                    try{f.setAccessible(true);Object v=f.get(config);if(v==null)continue;String s=v.toString();
                        if(s.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")&&!s.equals("00:00:00:00:00:00")&&!s.equals("02:00:00:00:00:00")){
                            int b0=Integer.parseInt(s.substring(0,2),16);if((b0&1)==0){address=s;events.log("[BSSID反射] 字段 "+f.getName()+" 提供地址（值不写日志）。");break;}
                        }
                    }catch(Throwable ignored){}
                }
            }
            boolean absent = address == null || address.toString().equals("02:00:00:00:00:00");
            String ssid=(String)config.getClass().getMethod("getSsid").invoke(config),password=(String)config.getClass().getMethod("getPassphrase").invoke(config);
            events.log("热点配置已读回；名称存在 "+(ssid!=null)+"，地址存在 "+(address!=null)+"，密码存在 "+(password!=null)+"。");
            SparseIntArray channels=(SparseIntArray)config.getClass().getMethod("getChannels").invoke(config);
            if(channels.size()!=1||channels.get(2,0)!=149){events.failed("系统未保留指定的 5 GHz 信道配置");return;}
            Set<String> added=addresses();added.removeAll(oldAddresses);
            if(added.size()!=1){if(++attempts<30){handler.postDelayed(this::inspect,500);return;}events.failed("无法唯一确认本轮热点 IPv4 地址");return;}
            String owner=added.iterator().next();int dot=owner.lastIndexOf('.');String client=owner.substring(0,dot+1)+(owner.endsWith(".2")?"3":"2");
            // 部分 OEM(一加 ColorOS)通过所有公开/SystemApi 途径都不返回真实 BSSID。
            // 此时不死锁等待，直接用仅 SSID 凭据(协议允许)，让手机按 SSID+密码关联热点。
            if(absent){
                if(actualFrequency==0){
                    if(watch==null){
                        watch=new LocalApInfo(context,new LocalApInfo.Events(){
                            public void found(String bssid,int frequency){if(!closed){scannedBssid=bssid;actualFrequency=frequency;inspect();}}
                            public void failed(String reason){if(!closed)events.log("热点状态回调："+reason);}
                            public void log(String text){events.log(text);}
                            public void frequency(int frequency){if(!closed){actualFrequency=frequency;inspect();}}
                        },null);watch.start();
                    }
                    return;
                }
                String realBssid=scannedBssid!=null?scannedBssid:rootBssid(owner);
                NetworkResource.Offer offer;
                if(realBssid!=null){
                    offer=new NetworkResource.Offer(ssid,realBssid,password,owner,client,actualFrequency!=0?actualFrequency:5745,4005);
                    events.log(scannedBssid!=null?"使用本轮热点运行回调确认的 BSSID。":"使用 root 读取的热点 BSSID。");
                }else{
                    offer=NetworkResource.Offer.ssidOnly(ssid,password,owner,client,actualFrequency,4005);
                    events.log("系统未公开真实 BSSID，使用仅 SSID 凭据（手机按名称+密码关联）。");
                }
                delivered=true;
                if(watch!=null){watch.close();watch=null;}
                events.ready(offer);
                return;
            }
            NetworkResource.Offer offer = new NetworkResource.Offer(ssid,address.toString(),password,owner,client,actualFrequency!=0?actualFrequency:5745,4005);
            events.log("本轮热点配置与实际频道已就绪；开始蓝牙网络连接验证。");
            delivered=true; events.ready(offer);
        }catch(Exception e){events.failed("本地热点参数检查失败："+e.getClass().getSimpleName());}
    }
    @Override public void close(){closed=true;handler.removeCallbacksAndMessages(null);if(watch!=null){watch.close();watch=null;}if(reservation!=null){reservation.close();reservation=null;events.log("本轮本地热点保留请求已释放。");}}
}

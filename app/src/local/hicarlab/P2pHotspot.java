package local.hicarlab;

import android.content.*;
import android.net.wifi.p2p.*;
import android.os.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;

/** Owns only the temporary group created by this experiment. */
final class P2pHotspot implements AutoCloseable {
    interface Events {void ready(NetworkResource.Offer offer);void failed(String reason);void log(String message);}
    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private final Events events;
    private volatile boolean closed,created;
    private int attempts;
    private static final int[] FREQUENCIES={5745,2437}; // 5GHz ch149 优先，失败退 2.4GHz ch6
    private int freqIndex;
    P2pHotspot(Context c,Events events){context=c.getApplicationContext();this.events=events;manager=(WifiP2pManager)c.getSystemService(Context.WIFI_P2P_SERVICE);}
    void start(){
        if(Build.VERSION.SDK_INT<29||manager==null){events.failed("系统未提供所需 Wi-Fi Direct 接口");return;}
        try{
            channel=manager.initialize(context,Looper.getMainLooper(),()->{if(!closed)events.failed("Wi-Fi Direct 服务断开");});
            if(channel==null){events.failed("Wi-Fi Direct 初始化失败");return;}
            manager.requestGroupInfo(channel,existing->{
                if(closed)return;
                if(existing!=null){events.failed("已有 Wi-Fi Direct 连接，实验未修改它");return;}
                create();
            });
        }catch(RuntimeException e){events.failed("Wi-Fi Direct 接口拒绝："+e.getClass().getSimpleName());}
    }
    private void create(){
        final int frequency=FREQUENCIES[freqIndex];
        try{
            byte[] random=new byte[12];new SecureRandom().nextBytes(random);StringBuilder password=new StringBuilder();for(byte b:random)password.append(String.format(Locale.ROOT,"%02x",b&255));
            WifiP2pConfig.Builder builder=new WifiP2pConfig.Builder().setNetworkName("DIRECT-HLHiCarLab-"+password.substring(0,4))
                .setPassphrase(password.toString()).enablePersistentMode(false);
            if(frequency>0)builder.setGroupOperatingFrequency(frequency);
            WifiP2pConfig config=builder.build();
            events.log("正在创建本轮临时 Wi-Fi Direct 热点（"+freqLabel(frequency)+"）；不记录密码。");
            manager.createGroup(channel,config,new WifiP2pManager.ActionListener(){
                public void onSuccess(){created=true;if(closed){remove();return;}poll();}
                public void onFailure(int reason){
                    if(closed)return;
                    if(freqIndex+1<FREQUENCIES.length){
                        freqIndex++;events.log(freqLabel(frequency)+" 建组失败（系统代码 "+reason+"），自动降级重试 "+freqLabel(FREQUENCIES[freqIndex])+"。");
                        handler.postDelayed(()->{if(!closed)create();},400);
                    }else events.failed("临时热点创建失败，5GHz 与 2.4GHz 均尝试过；系统代码 "+reason);
                }
            });
        }catch(RuntimeException e){events.failed("热点配置失败："+e.getClass().getSimpleName());}
    }
    private static String freqLabel(int f){return f==2437?"2.4 GHz 信道 6":f==5745?"5 GHz 信道 149":(f/1000)+" GHz";}
    private void poll(){
        if(closed)return;
        manager.requestGroupInfo(channel,group->{
            if(closed)return;
            if(group==null){if(++attempts<30)handler.postDelayed(this::poll,500);else events.failed("临时热点就绪超时");return;}
            if(!group.isGroupOwner()){events.failed("未成为本轮热点所有者");return;}
            try{
                String bssid=null;
                // GO 设备地址(group owner deviceAddress)就是 P2P 组的 BSSID，公开 API，最可靠。
                try{
                    android.net.wifi.p2p.WifiP2pDevice go=group.getOwner();
                    if(go!=null&&go.deviceAddress!=null){bssid=go.deviceAddress;events.log("[BSSID诊断] getOwner().deviceAddress="+bssid);}
                }catch(Throwable t){events.log("[BSSID诊断] getOwner不可用:"+t.getClass().getSimpleName());}
                if(!usableMac(bssid)&&Build.VERSION.SDK_INT>=34){
                    try{Object address=group.getClass().getMethod("getGroupOwnerBssid").invoke(group);events.log("[BSSID诊断] getGroupOwnerBssid="+address);if(address!=null)bssid=address.toString();}
                    catch(Throwable t){events.log("[BSSID诊断] getGroupOwnerBssid不可用:"+t.getClass().getSimpleName());}
                }
                events.log("[BSSID诊断] groupInterface="+group.getInterface()+" networkName="+group.getNetworkName());
                String owner=null;NetworkInterface network=NetworkInterface.getByName(group.getInterface());
                if(network!=null)for(InterfaceAddress a:network.getInterfaceAddresses())if(a.getAddress() instanceof Inet4Address&&!a.getAddress().isLoopbackAddress()&&a.getNetworkPrefixLength()==24)owner=a.getAddress().getHostAddress();
                if(owner==null){if(++attempts<30){handler.postDelayed(this::poll,500);return;}events.failed("未取得本轮热点真实 IPv4 地址");return;}
                if(!usableMac(bssid))bssid=OwnGroupAddress.read(network,group.getInterface(),events);
                // 接口名可能对不上(group.getInterface() 与 java.net 视图不一致)，改用 IP 反查拥有 owner 地址的接口 MAC。
                if(!usableMac(bssid)){
                    try{
                        Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();
                        while(all.hasMoreElements()){
                            NetworkInterface ni=all.nextElement();
                            boolean hit=false;
                            for(InterfaceAddress a:ni.getInterfaceAddresses())if(a.getAddress() instanceof Inet4Address&&owner.equals(a.getAddress().getHostAddress()))hit=true;
                            if(hit){byte[] hw=ni.getHardwareAddress();if(hw!=null&&hw.length==6){StringBuilder sb=new StringBuilder();for(byte x:hw){if(sb.length()>0)sb.append(':');sb.append(String.format("%02x",x&255));}bssid=sb.toString();events.log("[BSSID诊断] IP反查接口 "+ni.getName()+" MAC="+bssid);}break;}
                        }
                    }catch(Throwable t){events.log("[BSSID诊断] IP反查失败:"+t.getClass().getSimpleName());}
                }
                if(!usableMac(bssid))bssid=rootBssid(owner);
                if(!usableMac(bssid)){events.failed("系统未向普通应用提供本轮热点 BSSID；未发送占位地址");return;}
                int dot=owner.lastIndexOf('.');String client=owner.substring(0,dot+1)+(owner.endsWith(".2")?"3":"2");
                NetworkResource.Offer offer=new NetworkResource.Offer(group.getNetworkName(),bssid,group.getPassphrase(),owner,client,group.getFrequency(),4005);
                events.log("临时热点已就绪：频率 "+group.getFrequency()+" MHz，IPv4 已取得（BSSID/密码值不写日志）。");
                events.ready(offer);
            }catch(Exception e){events.failed("热点参数核验失败："+e.getClass().getSimpleName());}
        });
    }
    private static boolean usableMac(String value){
        return value!=null&&value.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")&&!value.equals("02:00:00:00:00:00")&&!value.equals("00:00:00:00:00:00");
    }
    // 车机若被系统隐藏 BSSID，尝试用 root 读取持有本轮热点 IP 的接口 MAC。
    private String rootBssid(String owner){
        try{
            String script="for d in /sys/class/net/*; do dev=${d##*/}; "
                +"ip -4 addr show dev $dev 2>/dev/null | grep -q '"+owner+"' || continue; "
                +"a=$(cat $d/address 2>/dev/null); case $a in *:*:*) echo $a; break;; esac; done";
            java.lang.Process p=Runtime.getRuntime().exec(new String[]{"/system/bin/su","-c",script});
            java.io.BufferedReader r=new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line;String found=null;
            while((line=r.readLine())!=null){line=line.trim().toUpperCase(Locale.ROOT);
                if(line.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}"))found=line;}
            p.waitFor();
            if(found!=null&&(Integer.parseInt(found.substring(0,2),16)&1)==0){
                events.log("[root BSSID] 车机已授权 root，读取到本轮热点真实 BSSID（值不写日志）。");return found;
            }
        }catch(Exception e){}
        return null;
    }
    private void remove(){if(created&&channel!=null){created=false;manager.removeGroup(channel,new WifiP2pManager.ActionListener(){public void onSuccess(){events.log("本轮临时热点已移除。");}public void onFailure(int reason){events.log("临时热点移除未确认，系统代码 "+reason);}});}}
    @Override public void close(){closed=true;handler.removeCallbacksAndMessages(null);remove();}
}

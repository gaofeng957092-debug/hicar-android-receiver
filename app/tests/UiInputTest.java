package local.hicarlab;
import java.util.*;
import java.io.IOException;

public final class UiInputTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    static byte[] decode(byte[] record,SessionCipher peer)throws Exception{
        check(record[0]==0&&record[1]==0&&(((record[2]&255)<<8)|(record[3]&255))==record.length);
        return peer.decrypt(Arrays.copyOfRange(record,4,record.length));
    }
    static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte n:bytes)b.append(String.format(Locale.ROOT,"%02x",n&255));return b.toString();}
    public static void main(String[] args)throws Exception{
        byte[] key=new byte[32];for(int i=0;i<32;i++)key[i]=(byte)i;
        try(UiInput sender=new UiInput(key);SessionCipher peer=new SessionCipher(key,1500)){
            byte[] one=sender.encode(UiInput.Event.touch(0,799,479),0x01020304L),two=sender.encode(UiInput.Event.touch(0,799,479),0x01020304L);
            check(hex(decode(one,peer)).equals("ffffffffffff00000b0100031f01df0102030400"));check(!Arrays.equals(one,two));
            check(hex(decode(sender.encode(UiInput.Event.key(4,true),0),peer)).equals("ffffffffffff030009000004000000000000"));
            check(hex(decode(sender.encode(UiInput.Event.touch(0,2339,1079,1),0),peer)).equals("ffffffffffff00000b0101092304370000000000"));
            check(hex(decode(sender.encode(UiInput.Event.touches(0,new int[]{0,1},new int[]{10,2339},new int[]{20,1079},1),0x01020304L),peer)).equals("ffffffffffff0000100200000a001401092304370102030401"));
            for(UiInput.Event bad:new UiInput.Event[]{UiInput.Event.touch(0,2340,0),UiInput.Event.touch(0,0,1080),UiInput.Event.touch(0,-1,0),UiInput.Event.key(85,true)}){
                boolean rejected=false;try{sender.encode(bad,0);}catch(IOException e){rejected=true;}check(rejected);
            }
        }
        check(Arrays.equals(InputRouter.map(1199,719,1200,720,false),new int[]{2338,1078}));
        check(InputRouter.map(1200,720,1200,720,false)==null);
        check(Arrays.equals(InputRouter.map(2000,-20,1200,720,true),new int[]{2339,0}));
        check(InputRouter.map(Float.NaN,0,1200,720,true)==null&&InputRouter.map(0,0,0,720,true)==null);
        InputRouter router=new InputRouter();List<UiInput.Event> first=new ArrayList<>(),second=new ArrayList<>();InputRouter.Sender a=e->{first.add(e);return true;},b=e->{second.add(e);return true;};
        check(!router.touch(0,1,1));router.attach(a);check(!router.touch(1,1,1));check(router.touch(0,1,1)&&router.touch(2,2,3));router.cancel();router.cancel();
        check(first.size()==3&&first.get(2).action==1&&first.get(2).x==2&&first.get(2).y==3);
        router.attach(b);router.detach(a);check(router.ready());check(!router.touch(2,2,3));check(router.key(4));check(second.size()==2&&second.get(0).action==3&&second.get(1).action==4);
        router.detach(b);check(!router.ready()&&!router.key(3));
        second.clear();router.attach(b);
        check(router.touch(0,2000,1000,0)&&router.touch(0,2339,1079,1));
        check(router.touch(2,2100,1000,0)&&router.touch(2,2200,1000,1));
        check(!router.touch(0,2340,0,2)&&!router.touch(0,1,1,5));
        router.cancelAll();router.cancelAll();
        check(second.size()==6&&second.get(4).pointer==1&&second.get(5).pointer==0&&second.get(4).action==1&&second.get(5).action==1);
        check(second.get(1).pointers.length==2&&second.get(1).actionIndex==1);
        check(second.get(4).pointers.length==2&&second.get(5).pointers.length==1);
        second.clear();router.attach(b);
        check(router.touchFrame(0,new int[]{0},new int[]{1000},new int[]{500},0));
        check(router.touchFrame(0,new int[]{0,1},new int[]{1000,1300},new int[]{500,500},1));
        check(router.touchFrame(2,new int[]{0,1},new int[]{900,1400},new int[]{500,500},0));
        check(!router.touchFrame(2,new int[]{0},new int[]{900},new int[]{500},0));
        check(!router.touchFrame(2,new int[]{0,0},new int[]{900,1400},new int[]{500,500},0));
        check(router.touchFrame(1,new int[]{0,1},new int[]{900,1400},new int[]{500,500},0));
        check(router.touchFrame(2,new int[]{1},new int[]{1500},new int[]{500},0));
        check(router.touchFrame(0,new int[]{1,0},new int[]{1500,800},new int[]{500,500},1));
        check(second.size()==6&&second.get(2).pointers.length==2&&second.get(2).xs[0]==900&&second.get(2).xs[1]==1400);
        int[] ids={1,0},xs={1500,800},ys={500,500};
        UiInput.Event snapshot=UiInput.Event.touches(2,ids,xs,ys,0);ids[0]=4;xs[0]=0;ys[0]=0;
        check(snapshot.pointers[0]==1&&snapshot.xs[0]==1500&&snapshot.ys[0]==500);
        router.cancelAll();router.detach(b);
        System.out.println("PASS: Python-compatible UIBC plaintext, fresh IVs, bounds/scaling/clamping, down/move/up/cancel, key pairing and stale-session isolation");
    }
}

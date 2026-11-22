package local.hicarlab;
import java.io.IOException;
import java.nio.*;
import java.security.GeneralSecurityException;

/** CarLink hisight_input.py (MIT): encrypted UIBC, only explicit local UI events. */
final class UiInput implements AutoCloseable {
    static final class Event {
        final int action,x,y,key,pointer,actionIndex;
        final int[] pointers,xs,ys;
        private Event(int action,int key,int[] pointers,int[] xs,int[] ys,int actionIndex){
            this.action=action;this.key=key;this.actionIndex=actionIndex;
            this.pointers=pointers.clone();this.xs=xs.clone();this.ys=ys.clone();
            boolean contact=actionIndex>=0&&actionIndex<pointers.length&&actionIndex<xs.length&&actionIndex<ys.length;
            pointer=contact?pointers[actionIndex]:0;x=contact?xs[actionIndex]:0;y=contact?ys[actionIndex]:0;
        }
        static Event touch(int a,int x,int y){return touch(a,x,y,0);}
        static Event touch(int a,int x,int y,int pointer){return touches(a,new int[]{pointer},new int[]{x},new int[]{y},0);}
        static Event touches(int a,int[] pointers,int[] xs,int[] ys,int actionIndex){return new Event(a,0,pointers,xs,ys,actionIndex);}
        static Event key(int key,boolean down){return new Event(down?3:4,key,new int[0],new int[0],new int[0],0);}
    }
    private final SessionCipher cipher;
    UiInput(byte[] key){cipher=new SessionCipher(key,1500);}
    byte[] encode(Event event,long elapsedMs)throws IOException,GeneralSecurityException{
        ByteBuffer body;
        if(event.action>=0&&event.action<=2){
            int count=event.pointers.length;
            if(count<1||count>5||event.xs.length!=count||event.ys.length!=count||event.actionIndex<0||event.actionIndex>=count||elapsedMs<0)throw new IOException("touch contacts");
            boolean[] seen=new boolean[5];
            for(int i=0;i<count;i++){
                int p=event.pointers[i];
                if(p<0||p>=5||seen[p]||event.xs[i]<0||event.xs[i]>=VideoProfile.width()||event.ys[i]<0||event.ys[i]>=VideoProfile.height())throw new IOException("touch coordinates");
                seen[p]=true;
            }
            // One event carries the complete contact set. UP still includes the
            // lifted contact; the trailing index identifies the changed contact.
            int payloadLength=6+5*count;
            body=ByteBuffer.allocate(3+payloadLength).order(ByteOrder.BIG_ENDIAN);
            body.put((byte)event.action).putShort((short)payloadLength).put((byte)count);
            for(int i=0;i<count;i++)body.put((byte)event.pointers[i]).putShort((short)event.xs[i]).putShort((short)event.ys[i]);
            body.putInt((int)(elapsedMs&0xffffffffL)).put((byte)event.actionIndex);
        }else if(event.action==3||event.action==4){
            if(event.key!=3&&event.key!=4)throw new IOException("unsupported input key");
            body=ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);body.put((byte)event.action).putShort((short)9).put((byte)0).putShort((short)event.key).putShort((short)0).putInt(0);
        }else throw new IOException("input action");
        byte[] plain=NearbyWire.join(new byte[]{-1,-1,-1,-1,-1,-1},body.array());
        byte[] encrypted;
        try{encrypted=cipher.encrypt(plain);}finally{java.util.Arrays.fill(plain,(byte)0);}
        int length=encrypted.length+4;return NearbyWire.join(new byte[]{0,0,(byte)(length>>>8),(byte)length},encrypted);
    }
    public void close(){cipher.close();}
}

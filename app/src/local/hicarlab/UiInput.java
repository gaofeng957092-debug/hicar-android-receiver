package local.hicarlab;
import java.io.IOException;
import java.nio.*;
import java.security.GeneralSecurityException;

/** CarLink hisight_input.py (MIT): encrypted UIBC, only explicit local UI events. */
final class UiInput implements AutoCloseable {
    static final class Event {
        final int action,x,y,key,pointer;
        private Event(int a,int x,int y,int key,int pointer){action=a;this.x=x;this.y=y;this.key=key;this.pointer=pointer;}
        static Event touch(int a,int x,int y){return new Event(a,x,y,0,0);}
        static Event touch(int a,int x,int y,int pointer){return new Event(a,x,y,0,pointer);}
        static Event key(int key,boolean down){return new Event(down?3:4,0,0,key,0);}
    }
    private final SessionCipher cipher;
    UiInput(byte[] key){cipher=new SessionCipher(key,1500);}
    byte[] encode(Event event,long elapsedMs)throws IOException,GeneralSecurityException{
        ByteBuffer body;
        if(event.action>=0&&event.action<=2){
            if(event.x<0||event.x>=VideoProfile.width()||event.y<0||event.y>=VideoProfile.height()||elapsedMs<0)throw new IOException("touch coordinates");
            body=ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN);
            body.put((byte)event.action).putShort((short)11).put((byte)1).put((byte)(event.pointer&0xff)).putShort((short)event.x).putShort((short)event.y).putInt((int)(elapsedMs&0xffffffffL)).put((byte)0);
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

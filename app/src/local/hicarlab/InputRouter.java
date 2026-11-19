package local.hicarlab;

/** Only the current authenticated input session can receive UI events. */
final class InputRouter {
    interface Sender {boolean send(UiInput.Event event);}
    private static final int MAX_POINTERS=5;
    private Sender sender;private boolean pressed;private int x,y;
    private final boolean[] down=new boolean[MAX_POINTERS];private final int[] lastX=new int[MAX_POINTERS],lastY=new int[MAX_POINTERS];
    synchronized void attach(Sender next){sender=next;pressed=false;clearPointers();}
    synchronized void detach(Sender old){if(sender==old){sender=null;pressed=false;clearPointers();}}
    private void clearPointers(){for(int i=0;i<MAX_POINTERS;i++)down[i]=false;}
    synchronized boolean ready(){return sender!=null;}
    synchronized boolean touch(int action,int nx,int ny){return touch(action,nx,ny,0);}
    synchronized boolean touch(int action,int nx,int ny,int pointer){
        if(sender==null||nx<0||nx>=VideoProfile.width()||ny<0||ny>=VideoProfile.height()||action<0||action>2)return false;
        if(pointer<0||pointer>=MAX_POINTERS)return false;
        if(action==0&&down[pointer]||action!=0&&!down[pointer])return false;
        if(!sender.send(UiInput.Event.touch(action,nx,ny,pointer)))return false;
        lastX[pointer]=nx;lastY[pointer]=ny;down[pointer]=action!=1;
        if(pointer==0){x=nx;y=ny;pressed=down[0];}
        return true;
    }
    synchronized void cancel(){cancel(0);}
    synchronized void cancel(int pointer){
        if(pointer<0||pointer>=MAX_POINTERS)return;
        if(down[pointer]&&sender!=null)sender.send(UiInput.Event.touch(1,lastX[pointer],lastY[pointer],pointer));
        down[pointer]=false;if(pointer==0)pressed=false;
    }
    synchronized void cancelAll(){for(int i=0;i<MAX_POINTERS;i++)cancel(i);}
    synchronized boolean key(int key){
        if(sender==null||(key!=3&&key!=4))return false;
        cancelAll();if(!sender.send(UiInput.Event.key(key,true)))return false;return sender.send(UiInput.Event.key(key,false));
    }
    static int[] map(float x,float y,int width,int height,boolean clamp){
        if(width<1||height<1||Float.isNaN(x)||Float.isInfinite(x)||Float.isNaN(y)||Float.isInfinite(y))return null;
        if(!clamp&&(x<0||x>=width||y<0||y>=height))return null;
        int px=Math.max(0,Math.min(VideoProfile.width()-1,(int)Math.floor(x*(double)VideoProfile.width()/width)));
        int py=Math.max(0,Math.min(VideoProfile.height()-1,(int)Math.floor(y*(double)VideoProfile.height()/height)));
        return new int[]{px,py};
    }
}

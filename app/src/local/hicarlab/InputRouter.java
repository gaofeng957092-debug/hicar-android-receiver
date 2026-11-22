package local.hicarlab;

/** Only the current authenticated input session can receive UI events. */
final class InputRouter {
    interface Sender {boolean send(UiInput.Event event);}
    private static final int MAX_POINTERS=5;
    private Sender sender;
    private final boolean[] down=new boolean[MAX_POINTERS];private final int[] lastX=new int[MAX_POINTERS],lastY=new int[MAX_POINTERS];
    synchronized void attach(Sender next){sender=next;clearPointers();}
    synchronized void detach(Sender old){if(sender==old){sender=null;clearPointers();}}
    private void clearPointers(){for(int i=0;i<MAX_POINTERS;i++)down[i]=false;}
    synchronized boolean ready(){return sender!=null;}
    synchronized boolean touch(int action,int nx,int ny){return touch(action,nx,ny,0);}
    synchronized boolean touch(int action,int nx,int ny,int pointer){
        if(pointer<0||pointer>=MAX_POINTERS||action<0||action>2)return false;
        int count=0;for(int i=0;i<MAX_POINTERS;i++)if(down[i]||i==pointer)count++;
        int[] ids=new int[count],xs=new int[count],ys=new int[count];int index=0,changed=0;
        for(int i=0;i<MAX_POINTERS;i++)if(down[i]||i==pointer){
            ids[index]=i;xs[index]=i==pointer?nx:lastX[i];ys[index]=i==pointer?ny:lastY[i];
            if(i==pointer)changed=index;index++;
        }
        return touchFrame(action,ids,xs,ys,changed);
    }
    synchronized boolean touchFrame(int action,int[] ids,int[] xs,int[] ys,int changed){
        int count=ids.length;
        if(sender==null||action<0||action>2||count<1||count>MAX_POINTERS||xs.length!=count||ys.length!=count||changed<0||changed>=count)return false;
        boolean[] seen=new boolean[MAX_POINTERS];
        for(int i=0;i<count;i++){
            int id=ids[i];
            if(id<0||id>=MAX_POINTERS||seen[id]||xs[i]<0||xs[i]>=VideoProfile.width()||ys[i]<0||ys[i]>=VideoProfile.height())return false;
            boolean adding=action==0&&i==changed;
            if(adding?down[id]:!down[id])return false;
            seen[id]=true;
        }
        for(int id=0;id<MAX_POINTERS;id++)if(down[id]&&!seen[id])return false;
        if(!sender.send(UiInput.Event.touches(action,ids,xs,ys,changed)))return false;
        for(int i=0;i<count;i++){lastX[ids[i]]=xs[i];lastY[ids[i]]=ys[i];down[ids[i]]=true;}
        if(action==1)down[ids[changed]]=false;
        return true;
    }
    synchronized void cancel(){cancel(0);}
    synchronized void cancel(int pointer){
        if(pointer<0||pointer>=MAX_POINTERS)return;
        if(down[pointer]&&sender!=null)touch(1,lastX[pointer],lastY[pointer],pointer);
        down[pointer]=false;
    }
    synchronized void cancelAll(){for(int i=MAX_POINTERS-1;i>=0;i--)cancel(i);}
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

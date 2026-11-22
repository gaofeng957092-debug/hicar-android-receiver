package local.hicarlab;

/** Coalesce media/control disconnects from one generation into one restart. */
final class ReconnectGate {
    private static final long PEER_SILENCE_NANOS=15_000_000_000L;
    private int pending;
    private boolean requested;
    synchronized boolean request(int generation) {
        if(requested && pending==generation)return false;
        pending=generation;requested=true;return true;
    }
    synchronized void reset(){requested=false;}
    static boolean peerTimedOut(long now,long lastControl,long lastMedia){
        return now-lastControl>=PEER_SILENCE_NANOS&&now-lastMedia>=PEER_SILENCE_NANOS;
    }
}

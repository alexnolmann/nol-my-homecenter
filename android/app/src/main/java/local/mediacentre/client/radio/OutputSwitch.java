package local.mediacentre.client.radio;

/** Bounded route-settling policy, independent of Android and playback lifecycles. */
public final class OutputSwitch {
    public static final int WAITING=0, READY_IDLE=1, READY_RESUME=2, TIMED_OUT=3, STALE=4;
    public static final long TIMEOUT_MS=20000, SETTLE_MS=750;
    private long epoch, deadline, readySince=-1;
    private boolean busy, resume;
    public synchronized long begin(long now, boolean shouldResume) {
        epoch++; busy=true; resume=shouldResume; deadline=now+TIMEOUT_MS; readySince=-1; return epoch;
    }
    public synchronized boolean busy(){return busy;}
    public synchronized void cancelResume(){resume=false;}
    public synchronized void cancel(){epoch++;busy=false;resume=false;readySince=-1;}
    public synchronized int poll(long request,long now,boolean linkReady,boolean routeReady){
        if(!busy||request!=epoch)return STALE;
        if(now>=deadline){busy=false;resume=false;return TIMED_OUT;}
        if(!linkReady||!routeReady){readySince=-1;return WAITING;}
        if(readySince<0)readySince=now;
        if(now-readySince<SETTLE_MS)return WAITING;
        busy=false;boolean play=resume;resume=false;return play?READY_RESUME:READY_IDLE;
    }
}

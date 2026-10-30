package local.mediacentre.client.radio;

/** Retry only a current user-started session; no persisted auto-play intent. */
public final class RadioRetry {
    private long epoch;private int failures;private boolean wanted;
    public long start(){wanted=true;failures=0;return ++epoch;}
    public void stop(){wanted=false;failures=0;epoch++;}
    public boolean current(long request){return wanted&&request==epoch;}
    public long epoch(){return epoch;}
    public int failures(){return failures;}
    public long failed(long request){if(!current(request))return -1;long delay=Math.min(30000L,1000L<<Math.min(failures,5));failures=Math.min(failures+1,32);return delay;}
    public void stable(long request){if(current(request))failures=0;}
}

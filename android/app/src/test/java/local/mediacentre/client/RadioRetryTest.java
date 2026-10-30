package local.mediacentre.client;
import local.mediacentre.client.radio.RadioRetry;
import org.junit.Test;
import static org.junit.Assert.*;
public final class RadioRetryTest {
    @Test public void repeatedFailuresBackOffAndStablePlaybackResetsDelay(){
        RadioRetry retry=new RadioRetry();long session=retry.start();for(long delay:new long[]{1000,2000,4000,8000,16000,30000,30000})assertEquals(delay,retry.failed(session));
        for(int n=0;n<1000;n++)assertEquals(30000,retry.failed(session));retry.stable(session);assertEquals(1000,retry.failed(session));
    }
    @Test public void stopAndSwitchRejectAllOldCallbacksAndCannotRestartByGain(){
        RadioRetry retry=new RadioRetry();assertEquals(-1,retry.failed(retry.epoch()));long old=retry.start();retry.failed(old);retry.stop();assertFalse(retry.current(old));assertEquals(-1,retry.failed(old));retry.stable(old);assertEquals(-1,retry.failed(retry.epoch()));
        long current=retry.start();assertFalse(retry.current(old));assertTrue(retry.current(current));assertEquals(1000,retry.failed(current));retry.stable(old);assertEquals(2000,retry.failed(current));
    }
}

package local.mediacentre.client;

import local.mediacentre.client.radio.WaveformCapture;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WaveformCaptureTest {
    private static final class Backend implements WaveformCapture.Factory {
        final List<String> events=new ArrayList<>();boolean failOpen,failRead;int reads;byte value=(byte)128;
        public WaveformCapture.Source open(int session){
            events.add("open:"+session);if(failOpen)throw new IllegalStateException();
            return new WaveformCapture.Source(){
                public int size(){return 512;}
                public boolean read(byte[] out){reads++;Arrays.fill(out,value);return !failRead;}
                public void close(){events.add("close:"+session);}
            };
        }
    }
    @Test public void neverCapturesGlobalMixOrHiddenPlayback(){
        Backend b=new Backend();WaveformCapture c=new WaveformCapture(b);
        c.target(0,true);c.target(-1,true);c.target(7,false);
        assertFalse(c.capturing());assertTrue(b.events.isEmpty());
    }
    @Test public void hidingReleasesAndStopsReadingButCanResume(){
        Backend b=new Backend();WaveformCapture c=new WaveformCapture(b);c.target(7,true);c.target(7,true);
        assertEquals(Arrays.asList("open:7"),b.events);c.read(new float[512]);c.target(7,false);
        float[] output=new float[]{1,1};assertFalse(c.read(output));assertArrayEquals(new float[]{0,0},output,0);
        assertEquals(1,b.reads);assertEquals(Arrays.asList("open:7","close:7"),b.events);
        c.target(7,true);assertTrue(c.capturing());
    }
    @Test public void replacingPlayerClosesOldEffectBeforeOpeningNewOne(){
        Backend b=new Backend();WaveformCapture c=new WaveformCapture(b);c.target(7,true);c.target(0,true);c.target(9,true);
        assertEquals(Arrays.asList("open:7","close:7","open:9"),b.events);
    }
    @Test public void captureFailureClearsTheLineWithoutRetryingEveryFrame(){
        Backend b=new Backend();WaveformCapture c=new WaveformCapture(b);c.target(7,true);b.failRead=true;
        float[] output=new float[]{1,1};assertFalse(c.read(output));assertArrayEquals(new float[]{0,0},output,0);
        c.target(7,true);c.read(output);assertEquals(1,b.reads);
        assertEquals(Arrays.asList("open:7","close:7"),b.events);
        b.failRead=false;c.target(9,true);assertTrue(c.capturing());
    }
    @Test public void setupFailureWaitsForNewVisibilityOrSession(){
        Backend b=new Backend();b.failOpen=true;WaveformCapture c=new WaveformCapture(b);
        c.target(7,true);c.target(7,true);assertEquals(1,b.events.size());assertFalse(c.capturing());
        b.failOpen=false;c.target(7,false);c.target(7,true);assertTrue(c.capturing());
    }
    @Test public void unsignedPcmHasCorrectSilenceAndPolarity(){
        Backend b=new Backend();WaveformCapture c=new WaveformCapture(b);c.target(7,true);float[] out=new float[512];
        assertTrue(c.read(out));assertEquals(0,out[0],0);
        b.value=0;c.read(out);assertEquals(-1,out[0],0);
        b.value=(byte)255;c.read(out);assertEquals(127/128f,out[0],0);
        c.close();c.close();assertEquals(Arrays.asList("open:7","close:7"),b.events);
    }
}

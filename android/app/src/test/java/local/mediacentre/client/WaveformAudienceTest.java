package local.mediacentre.client;

import local.mediacentre.client.radio.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WaveformAudienceTest {
    private static final class Backend implements WaveformCapture.Factory {
        final List<String> events=new ArrayList<>();boolean failed;
        public WaveformCapture.Source open(int session){events.add("open:"+session);return new WaveformCapture.Source(){public int size(){return 128;}public boolean read(byte[] out){Arrays.fill(out,(byte)128);return !failed;}public void close(){events.add("close:"+session);}};}
    }
    @Test public void captureLivesUntilLastViewerLeavesAndRepeatedVisibilityIsIdempotent(){
        Backend backend=new Backend();WaveformAudience audience=new WaveformAudience(new WaveformCapture(backend));Object first=new Object(),second=new Object();
        audience.target(7);assertTrue(backend.events.isEmpty());audience.visible(first,true);audience.visible(first,true);audience.visible(second,true);audience.visible(first,false);
        assertEquals(Arrays.asList("open:7"),backend.events);audience.visible(second,false);assertEquals(Arrays.asList("open:7","close:7"),backend.events);
    }
    @Test public void handoffClosesEffectBeforeOpeningNewPlayerAndNeverCapturesSessionZero(){
        Backend backend=new Backend();WaveformAudience audience=new WaveformAudience(new WaveformCapture(backend));audience.visible(this,true);audience.target(7);audience.target(0);audience.target(9);
        assertEquals(Arrays.asList("open:7","close:7","open:9"),backend.events);
    }
    @Test public void hiddenPlaybackNeitherCapturesNorReadsAndResumingUsesLatestPlayer(){
        Backend backend=new Backend();WaveformAudience audience=new WaveformAudience(new WaveformCapture(backend));audience.target(7);audience.target(9);float[] data={1,1};assertFalse(audience.read(data));assertArrayEquals(new float[]{0,0},data,0);assertTrue(backend.events.isEmpty());
        audience.visible(this,true);assertEquals(Arrays.asList("open:9"),backend.events);
    }
    @Test public void additionalViewerDoesNotRetryFailedCaptureUntilEveryoneHides(){
        Backend backend=new Backend();WaveformAudience audience=new WaveformAudience(new WaveformCapture(backend));Object first=new Object(),second=new Object();audience.target(7);audience.visible(first,true);backend.failed=true;assertFalse(audience.read(new float[512]));
        audience.visible(second,true);audience.visible(first,false);assertEquals(Arrays.asList("open:7","close:7"),backend.events);
        audience.visible(second,false);backend.failed=false;audience.visible(first,true);assertEquals(Arrays.asList("open:7","close:7","open:7"),backend.events);
    }
}

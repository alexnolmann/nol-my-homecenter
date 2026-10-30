package local.mediacentre.client;
import local.mediacentre.client.climate.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClimatePollPolicyTest {
    @Test public void restartingWithFreshCacheDoesNotReconnect(){
        ClimateReading latest=new ClimateReading(21,50,3000,1_000_000);
        assertFalse(ClimatePollPolicy.due(latest,null,1000,1_010_000));
        assertTrue(ClimatePollPolicy.due(latest,null,1000,1_900_000));
    }
    @Test public void missingOrFutureCacheNeedsAnInitialMeasurement(){
        assertTrue(ClimatePollPolicy.due(null,null,1000,1_000_000));
        assertTrue(ClimatePollPolicy.due(new ClimateReading(21,50,3000,1_100_000),null,1000,1_000_000));
    }
    @Test public void failedAndCancelledReadsWaitForTheNextInterval(){
        assertFalse(ClimatePollPolicy.due(null,1000L,1001,1_000_000));
        assertFalse(ClimatePollPolicy.due(null,1000L,900_999,9_000_000));
        assertTrue(ClimatePollPolicy.due(null,1000L,901_000,9_000_000));
    }
    @Test public void panelIntervalChangesBackoffWithoutForcingAReconnect(){
        assertEquals(900_000L,ClimatePollPolicy.INTERVAL_MS);
        assertTrue(ClimatePollPolicy.due(null,1000L,301_000,1_000_000,300_000));
        assertFalse(ClimatePollPolicy.due(null,1000L,901_000,1_000_000,1_800_000));
    }
    @Test public void changingWallClockDoesNotBypassErrorBackoff(){
        assertFalse(ClimatePollPolicy.due(null,1000L,2000,100));
        assertFalse(ClimatePollPolicy.due(null,1000L,2000,Long.MAX_VALUE));
    }
}

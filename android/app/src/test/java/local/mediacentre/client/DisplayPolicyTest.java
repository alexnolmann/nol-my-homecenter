package local.mediacentre.client;
import org.junit.Test;
import static org.junit.Assert.*;
import local.mediacentre.client.presentation.DisplayPolicy;
public final class DisplayPolicyTest {
    @Test public void nightCrossesMidnightAndEndIsExclusive(){assertFalse(DisplayPolicy.night(21*60+59,22,7));assertTrue(DisplayPolicy.night(22*60,22,7));assertTrue(DisplayPolicy.night(0,22,7));assertTrue(DisplayPolicy.night(6*60+59,22,7));assertFalse(DisplayPolicy.night(7*60,22,7));assertTrue(DisplayPolicy.night(12*60,8,16));assertFalse(DisplayPolicy.night(16*60,8,16));assertFalse(DisplayPolicy.night(12*60,8,8));assertFalse(DisplayPolicy.night(-1,22,7));}
    @Test public void nightNeverBrightensScreenAndZeroCannotBlackOutControls(){assertEquals(10,DisplayPolicy.brightness(80,10,true,23*60,22,7));assertEquals(80,DisplayPolicy.brightness(80,10,false,23*60,22,7));assertEquals(5,DisplayPolicy.brightness(0,0,true,23*60,22,7));assertEquals(20,DisplayPolicy.brightness(20,90,true,23*60,22,7));assertEquals(100,DisplayPolicy.percent(200));}
    @Test public void autostartRequiresOptInAndCompatiblePlatform(){assertFalse(DisplayPolicy.bootAllowed(false,19));assertTrue(DisplayPolicy.bootAllowed(true,19));assertTrue(DisplayPolicy.bootAllowed(true,28));assertFalse(DisplayPolicy.bootAllowed(true,29));assertFalse(DisplayPolicy.bootAllowed(true,36));}
}

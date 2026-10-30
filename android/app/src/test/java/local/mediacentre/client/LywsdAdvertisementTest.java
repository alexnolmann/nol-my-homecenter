package local.mediacentre.client;
import local.mediacentre.client.climate.LywsdAdvertisement;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class LywsdAdvertisementTest {
    @Test public void recognizesTheStockNameWithoutAnAdvertisement(){
        assertTrue(LywsdAdvertisement.matches("LYWSD03MMC",null));
        assertFalse(LywsdAdvertisement.matches("Other thermometer",null));
    }
    @Test public void readsTheAdvertisedNameWithoutRequiringCachedDeviceName(){
        byte[] name="LYWSD03MMC".getBytes(StandardCharsets.UTF_8),packet=new byte[name.length+2];
        packet[0]=(byte)(name.length+1);packet[1]=9;System.arraycopy(name,0,packet,2,name.length);
        assertTrue(LywsdAdvertisement.matches(null,packet));
    }
    @Test public void acceptsOnlyTheCorrectXiaomiProduct(){
        byte[] packet={7,0x16,(byte)0x95,(byte)0xfe,0x50,0x20,0x5b,5};
        assertTrue(LywsdAdvertisement.matches(null,packet));
        packet[6]=0x5c;assertFalse(LywsdAdvertisement.matches(null,packet));
    }
    @Test public void ignoresTruncatedAdvertisementWithoutCrashing(){
        assertFalse(LywsdAdvertisement.matches(null,new byte[]{7,0x16,(byte)0x95}));
        assertFalse(LywsdAdvertisement.matches(null,new byte[]{0}));
    }
}

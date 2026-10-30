package local.mediacentre.client;
import org.junit.Test;
import static org.junit.Assert.*;
import local.mediacentre.client.radio.RadioStation;
public final class RadioStationTest {
    private static final String ID="10000000-0000-4000-8000-000000000001";
    @Test public void originalPublicUrlIsUnmodified(){String url="http://radio.example:8000/original.mp3?quality=128";RadioStation s=new RadioStation(ID,"Radio",url);assertEquals(url,s.url);assertEquals("Radio",s.name);}
    @Test public void credentialsAndNonNetworkSourcesAreRejected(){for(String url:new String[]{"file:///etc/passwd","javascript:play()","https://user:secret@radio.example/a","http://radio.example/a#fragment","https://radio.example:0/a","https://radio.example:70000/a","http://radio.example/white space"}){try{new RadioStation(ID,"Radio",url);fail("Invalid stream accepted.");}catch(IllegalArgumentException expected){}}}
}

package local.mediacentre.client;
import local.mediacentre.client.climate.*;
import org.json.JSONObject;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClimateConfigurationTest {
    private ClimateConfiguration config(long revision,int minutes,String... names){
        List<ClimateSensor> sensors=new ArrayList<>();
        for(int n=0;n<names.length;n+=2)sensors.add(new ClimateSensor(names[n],names[n+1],null,Collections.emptyList()));
        return new ClimateConfiguration(revision,minutes,true,sensors);
    }
    private static final String A="AA:BB:CC:DD:EE:01",B="AA:BB:CC:DD:EE:02",C="AA:BB:CC:DD:EE:03",D="AA:BB:CC:DD:EE:04";
    @Test public void serverIntervalWinsWhileIndependentRoomChangesMerge(){
        ClimateConfiguration base=config(1,15,A,"Кухня",B,"Комната");
        ClimateConfiguration merged=ClimateConfiguration.merge(base,config(1,15,A,"Спальня",B,"Комната"),config(2,30,A,"Кухня",B,"Гостиная"));
        assertEquals(30,merged.pollMinutes);assertEquals(2,merged.revision);assertEquals("Спальня",merged.sensors.get(0).name);assertEquals("Гостиная",merged.sensors.get(1).name);
    }
    @Test public void staleLocalCacheCannotResurrectServerDeletion(){
        ClimateConfiguration base=config(1,15,A,"Комната");
        assertTrue(ClimateConfiguration.merge(base,base,config(2,15)).sensors.isEmpty());
    }
    @Test public void sameEditInBothPlacesIsNotAConflict(){
        assertEquals("Спальня",ClimateConfiguration.merge(config(1,15,A,"Комната"),config(1,15,A,"Спальня"),config(2,15,A,"Спальня")).sensors.get(0).name);
    }
    @Test public void renameVersusDeleteRequiresExplicitChoice(){
        try{ClimateConfiguration.merge(config(1,15,A,"Комната"),config(1,15,A,"Спальня"),config(2,15));fail();}catch(IllegalStateException expected){}
    }
    @Test public void competingRenamesRequireExplicitChoice(){
        try{ClimateConfiguration.merge(config(1,15,A,"Комната"),config(1,15,A,"Спальня"),config(2,15,A,"Кухня"));fail();}catch(IllegalStateException expected){}
    }
    @Test public void independentAddsCannotExceedThreeSensors(){
        try{ClimateConfiguration.merge(config(1,15,A,"1",B,"2"),config(1,15,A,"1",B,"2",C,"3"),config(2,15,A,"1",B,"2",D,"4"));fail();}catch(IllegalStateException expected){}
    }
    @Test public void wireRoundTripAndWriteExcludeReadOnlyFields()throws Exception{
        ClimateConfiguration original=config(3,15,A,"Комната");JSONObject request=original.request(7,30);
        assertFalse(request.has("initialized"));assertEquals(7,request.getLong("revision"));assertEquals(30,request.getInt("poll_minutes"));
        assertTrue(original.sameSensors(ClimateConfiguration.fromJson(new JSONObject(original.toJson().toString()))));
        assertFalse(request.getJSONArray("sensors").getJSONObject(0).has("latest"));
    }
    @Test public void configurationBoundsAndDuplicateIdsAreEnforced(){
        for(int minutes:new int[]{0,121})try{config(1,minutes);fail();}catch(IllegalArgumentException expected){}
        try{config(1,15,A,"1",A,"2");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void editDuringRequestMergesAgainstCapturedLocalConfiguration(){
        ClimateConfiguration captured=config(1,15,A,"Первое имя");
        ClimateConfiguration edited=config(1,15,A,"Новое имя");
        ClimateConfiguration acknowledged=config(2,30,A,"Первое имя",B,"Новая комната из панели");
        ClimateConfiguration merged=ClimateConfiguration.merge(captured,edited,acknowledged);
        assertEquals("Новое имя",merged.sensors.get(0).name);assertEquals(2,merged.sensors.size());assertEquals(30,merged.pollMinutes);
    }
    @Test public void pairingAndServerChangesHaveSeparateBaselines(){
        String a=ClimateConfiguration.binding("server","https://host","first-token");
        assertEquals(a,ClimateConfiguration.binding("server","https://host","first-token"));
        assertNotEquals(a,ClimateConfiguration.binding("server","https://host","second-token"));
        assertNotEquals(a,ClimateConfiguration.binding("another-server","https://host","first-token"));
        assertFalse(a.contains("first-token"));assertEquals(64,a.length());
    }
    @Test public void oneMinuteReadsKeepADayOfHistory(){
        ClimateSensor sensor=new ClimateSensor(A,"Комната",null,Collections.emptyList());
        for(int n=0;n<1440;n++)sensor=sensor.measured(new ClimateReading(20,50,3000,1_000+n*60_000L));
        assertEquals(288,sensor.history.size());
        assertTrue(sensor.latest.measuredAt-sensor.history.get(0).measuredAt>23*60*60_000L);
    }
}

package local.mediacentre.client;
import local.mediacentre.client.climate.*;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClimateTest {
    @Test public void decodesStockNotificationIncludingVoltage(){
        ClimateReading reading=ClimateReading.decode(new byte[]{(byte)0xf8,7,74,(byte)0xd6,11},1000);
        assertEquals(20.4,reading.temperature,0.001);assertEquals(74,reading.humidity);assertEquals(3030,reading.batteryMillivolts);
    }
    @Test public void decodesNegativeTemperatureAsSignedLittleEndian(){
        assertEquals(-5.5,ClimateReading.decode(new byte[]{(byte)0xda,(byte)0xfd,50,(byte)0xb8,11},1000).temperature,0.001);
    }
    @Test public void rejectsTruncatedAndImplausibleMeasurements(){
        for(byte[] value:new byte[][]{null,new byte[4],new byte[6],new byte[]{0,0,101,0,12},new byte[]{0,0,50,0,0}}){
            try{ClimateReading.decode(value,1000);fail("Invalid packet accepted");}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void oldOrFutureSamplesAreStale(){
        ClimateReading reading=new ClimateReading(21,50,3000,1_000_000);
        assertFalse(reading.stale(1_900_000));assertTrue(reading.stale(1_900_001));assertTrue(reading.stale(999_999));
    }
    @Test public void historySurvivesRestartAndRemainsBounded()throws Exception{
        ClimateSensor sensor=new ClimateSensor("a4:c1:38:01:02:03","Комната",null,Collections.emptyList());
        for(int n=0;n<400;n++)sensor=sensor.measured(new ClimateReading(20+n/100.0,50,3000,1000+n*300_000L));
        assertEquals(288,sensor.history.size());assertEquals("A4:C1:38:01:02:03",sensor.address);
        ClimateSensor restored=ClimateSensor.fromJson(new JSONObject(sensor.toJson().toString()));
        assertEquals(sensor.latest.measuredAt,restored.latest.measuredAt);assertEquals(288,restored.history.size());
        assertEquals("Спальня",restored.renamed(" Спальня ").name);
    }
    @Test public void frequentManualReadsDoNotReplaceTheWholeDay(){
        ClimateSensor sensor=new ClimateSensor("A4:C1:38:01:02:03","Комната",null,Collections.emptyList());
        sensor=sensor.measured(new ClimateReading(20,50,3000,1000));
        sensor=sensor.measured(new ClimateReading(21,51,3000,31_000));
        assertEquals(1,sensor.history.size());assertEquals(21,sensor.latest.temperature,0);
        sensor=sensor.measured(new ClimateReading(22,51,3000,300_000));
        assertEquals(2,sensor.history.size());
    }
    @Test public void clockRollbackDiscardsFutureHistory(){
        ClimateSensor sensor=new ClimateSensor("A4:C1:38:01:02:03","Комната",null,Collections.emptyList());
        sensor=sensor.measured(new ClimateReading(20,50,3000,100_000));
        sensor=sensor.measured(new ClimateReading(21,50,3000,10_000));
        assertEquals(1,sensor.history.size());
    }
}

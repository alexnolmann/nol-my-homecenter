package local.mediacentre.client.data;

import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import static org.junit.Assert.*;

public final class DirectWeatherClientTest {
    private JSONObject fixture()throws Exception {
        try(InputStream input=getClass().getClassLoader().getResourceAsStream("open-meteo.json")){
            assertNotNull(input);ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
            return new JSONObject(output.toString("UTF-8"));
        }
    }
    private JSONObject location()throws Exception{return new JSONObject().put("id","11111111-1111-4111-8111-111111111111").put("name","DST test")
        .put("timezone","Europe/Berlin").put("revision",1).put("latitude","52.500000").put("longitude","13.400000");}
    private long fetched()throws Exception {SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);format.setTimeZone(TimeZone.getTimeZone("UTC"));return format.parse("2026-03-27T23:18:00Z").getTime();}
    @Test public void normalizesDatesPeriodsAndMissingValuesLikeTheServer()throws Exception {
        JSONObject result=DirectWeatherClient.normalize(fixture(),location(),fetched());Dtos.Weather weather=new Dtos.Weather(result);
        assertEquals(7,weather.days.size());assertEquals("2026-04-03",weather.days.get(6).date);assertNull(weather.days.get(2).minimum);
        assertEquals("unknown",weather.days.get(6).condition);assertEquals("-2.3",weather.temperature.toPlainString());
        assertEquals(0,weather.periods.size());assertEquals("Open-Meteo · планшет",weather.meta.source);
        assertEquals("2026-03-27T23:18:00Z",weather.meta.fetchedAt);
    }
    @Test public void rejectsChangedLocationUnitsAndCalendar()throws Exception {
        JSONObject raw=fixture();raw.put("timezone","Europe/Moscow");try{DirectWeatherClient.normalize(raw,location(),fetched());fail();}catch(IOException expected){}
        raw=fixture();raw.getJSONObject("current_units").put("wind_speed_10m","km/h");try{DirectWeatherClient.normalize(raw,location(),fetched());fail();}catch(IOException expected){}
        raw=fixture();raw.getJSONObject("daily").getJSONArray("time").put(1,raw.getJSONObject("daily").getJSONArray("time").getLong(0));try{DirectWeatherClient.normalize(raw,location(),fetched());fail();}catch(IOException expected){}
        JSONObject bad=location().put("latitude","91");try{DirectWeatherClient.coordinate(bad,"latitude",-90,90);fail();}catch(IOException expected){}
    }
    @Test public void acceptsFourteenDaysAndSixUpcomingHourlyPeriods()throws Exception {
        JSONObject raw=fixture(),daily=raw.getJSONObject("daily");
        for(String key:new String[]{"time","weather_code","temperature_2m_min","temperature_2m_max","precipitation_sum"}){
            JSONArray values=daily.getJSONArray(key);for(int i=0;i<7;i++)values.put("time".equals(key)?values.getLong(i)+7*86400:values.get(i));
        }
        JSONArray hours=new JSONArray(),temperatures=new JSONArray(),codes=new JSONArray();long first=daily.getJSONArray("time").getLong(0);
        for(int i=0;i<14*24;i++){hours.put(first+i*3600);temperatures.put(5);codes.put(3);}
        raw.put("hourly_units",new JSONObject().put("time","unixtime").put("temperature_2m","°C"))
            .put("hourly",new JSONObject().put("time",hours).put("temperature_2m",temperatures).put("weather_code",codes));
        Dtos.Weather weather=new Dtos.Weather(DirectWeatherClient.normalize(raw,location(),fetched()));
        assertEquals(14,weather.days.size());assertEquals(6,weather.periods.size());
    }
}

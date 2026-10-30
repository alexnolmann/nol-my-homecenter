package local.mediacentre.client.data;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import static org.junit.Assert.*;

public final class MetNorwayWeatherClientTest {
    private static long time(String value)throws Exception {SimpleDateFormat parser=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);parser.setTimeZone(TimeZone.getTimeZone("UTC"));return parser.parse(value).getTime();}
    private static String iso(long value){SimpleDateFormat output=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);output.setTimeZone(TimeZone.getTimeZone("UTC"));return output.format(new Date(value));}
    private static JSONObject location()throws Exception{return new JSONObject().put("id","11111111-1111-4111-8111-111111111111").put("name","Санкт-Петербург").put("timezone","Europe/Moscow").put("revision",1).put("latitude","59.938630").put("longitude","30.314130");}
    private static JSONObject fixture()throws Exception {
        JSONArray series=new JSONArray();long start=time("2026-10-04T21:00:00Z");
        for(int i=0;i<40;i++){
            JSONObject details=new JSONObject().put("air_temperature",5.5).put("relative_humidity",70).put("wind_speed",2.5);
            JSONObject next=new JSONObject().put("summary",new JSONObject().put("symbol_code","lightrain")).put("details",new JSONObject().put("precipitation_amount",0.2));
            series.put(new JSONObject().put("time",iso(start+i*21600000L)).put("data",new JSONObject().put("instant",new JSONObject().put("details",details)).put("next_1_hours",next)));
        }
        JSONObject units=new JSONObject().put("air_temperature","celsius").put("relative_humidity","%").put("wind_speed","m/s").put("precipitation_amount","mm");
        return new JSONObject().put("properties",new JSONObject().put("meta",new JSONObject().put("updated_at","2026-10-05T11:00:00Z").put("units",units)).put("timeseries",series));
    }
    @Test public void normalizesKeylessForecastAndExpires()throws Exception {
        Dtos.Weather result=new Dtos.Weather(MetNorwayWeatherClient.normalize(fixture(),location(),time("2026-10-05T12:00:00Z"),"Mon, 05 Oct 2026 13:00:00 GMT"));
        assertTrue(result.days.size()>=7);assertEquals(6,result.periods.size());assertEquals("rain",result.condition);
        assertEquals("MET Norway · CC BY 4.0",result.attribution);assertEquals("2026-10-05T13:00:00Z",result.meta.validUntil);
    }
    @Test public void rejectsChangedUnits()throws Exception {
        JSONObject raw=fixture();raw.getJSONObject("properties").getJSONObject("meta").getJSONObject("units").put("wind_speed","km/h");
        try{MetNorwayWeatherClient.normalize(raw,location(),time("2026-10-05T12:00:00Z"),null);fail();}catch(java.io.IOException expected){}
    }
}

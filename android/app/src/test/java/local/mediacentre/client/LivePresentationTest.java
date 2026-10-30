package local.mediacentre.client;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.io.*;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.presentation.*;
public final class LivePresentationTest {
    @Test public void everyNormalizedWeatherConditionAndLocalTimeIsReadable(){for(String s:new String[]{"clear","partly_cloudy","cloudy","fog","drizzle","freezing_rain","rain","snow","thunderstorm"})assertNotEquals("Нет описания",Presenters.condition(s));assertEquals("27.09.2026 10:30",Presenters.time("2026-09-27T07:30:00Z","Europe/Moscow"));assertEquals("—",Presenters.time(null,"Europe/Moscow"));}
    @Test public void declaredDelayAndMissingComparisonNeverBecomeRealtime()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream input=getClass().getResourceAsStream("/success.json")){byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1)out.write(b,0,n);}
        JSONObject envelope=new JSONObject(out.toString("UTF-8")).getJSONObject("finance");JSONObject share=envelope.getJSONObject("data").getJSONArray("items").getJSONObject(3);share.put("access_mode","anonymous_delayed").put("comparison","none").put("reference_date",JSONObject.NULL);
        ScreenModel screen=Presenters.finance(new Dtos.Finance(envelope));String text=screen.rows.get(3).body;
        assertTrue(text.contains("заявленная задержка 15 мин"));assertTrue(text.contains("Сравнение недоступно"));assertTrue(text.contains("10:30 МСК"));assertFalse(text.contains("реальном времени"));
    }
}

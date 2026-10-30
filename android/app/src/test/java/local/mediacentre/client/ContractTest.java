package local.mediacentre.client;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.io.*;
import java.math.BigDecimal;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.presentation.*;

public final class ContractTest {
    private JSONObject fixture(String scenario)throws Exception {
        try(InputStream input=getClass().getResourceAsStream("/"+scenario+".json")) {
            assertNotNull("Shared fixture missing",input);
            ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int length;
            while((length=input.read(buffer))!=-1)output.write(buffer,0,length);
            return new JSONObject(output.toString("UTF-8"));
        }
    }
    @Test public void sharedScenariosAndAdditiveFields()throws Exception {
        for(String scenario:new String[]{"success","empty","partial","stale"}) {
            JSONObject f=fixture(scenario);
            assertFalse(new Dtos.Bootstrap(f.getJSONObject("bootstrap")).homeEnabled);
            Dtos.Weather weather=new Dtos.Weather(f.getJSONObject("weather"));
            Dtos.Finance finance=new Dtos.Finance(f.getJSONObject("finance"));
            assertTrue(weather.meta.testData);assertEquals(scenario,weather.meta.scenario);
            assertEquals(scenario.equals("empty")?0:7,weather.days.size());
            assertEquals(scenario.equals("empty")?0:6,finance.items.size());
            assertTrue(Presenters.weather(weather).status.contains("ТЕСТОВЫЕ ДАННЫЕ"));
            assertTrue(Presenters.finance(finance).status.contains(scenario));
            if(scenario.equals("stale"))assertTrue(Presenters.finance(finance).status.contains("УСТАРЕЛО"));
        }
    }
    @Test public void nullAndPrecisionNeverBecomeZeroOrDouble()throws Exception {
        JSONObject partial=fixture("partial");
        Dtos.Weather weather=new Dtos.Weather(partial.getJSONObject("weather"));
        Dtos.Finance finance=new Dtos.Finance(partial.getJSONObject("finance"));
        assertNull(weather.temperature);assertNull(weather.days.get(2).minimum);
        assertNull(finance.items.get(4).price);assertNull(finance.items.get(3).change);
        assertTrue(Presenters.weather(weather).rows.get(0).title.contains("—"));
        assertTrue(Presenters.finance(finance).rows.get(4).title.contains("—"));
        assertFalse(Presenters.finance(finance).rows.get(4).title.contains("null"));
        Dtos.Instrument exact=new Dtos.Finance(fixture("success").getJSONObject("finance")).items.get(5);
        assertEquals("9876.543210987",exact.price.toPlainString());
        assertNotEquals(exact.tradeAt,exact.fetchedAt);
        assertEquals("2026-09-27T07:30:00Z",exact.tradeAt);
        assertEquals("12345678901234567890.123456789",Dtos.decimal(new JSONObject("{\"price\":\"12345678901234567890.123456789\"}"),"price").toPlainString());
    }
    @Test public void numericMoneyIsRejected()throws Exception {
        try{Dtos.decimal(new JSONObject("{\"price\":12.3}"),"price");fail("Numeric price accepted");}catch(JSONException expected){}
    }
    @Test public void bootstrapUsesInstalledVersionAndScopedRadioFlag()throws Exception {
        JSONObject bootstrap=fixture("success").getJSONObject("bootstrap");
        bootstrap.getJSONObject("data").put("min_client_version",BuildConfig.VERSION_CODE);
        bootstrap.getJSONObject("data").getJSONObject("features").put("radio",false);
        assertFalse(new Dtos.Bootstrap(bootstrap).radioEnabled);
        bootstrap.getJSONObject("data").put("min_client_version",BuildConfig.VERSION_CODE+1);
        try{new Dtos.Bootstrap(bootstrap);fail("Newer mandatory client version accepted");}catch(JSONException expected){}
    }
    @Test public void sourceNominalDoesNotChangeUnitPriceLabel()throws Exception {
        JSONObject finance=fixture("success").getJSONObject("finance");
        finance.getJSONObject("data").getJSONArray("items").getJSONObject(0).put("price","15.0003").put("nominal","10");
        ScreenModel screen=Presenters.finance(new Dtos.Finance(finance));
        assertTrue(screen.rows.get(0).title.contains("15.0003"));
        assertTrue(screen.rows.get(0).body.contains("за 1 USD"));
        assertTrue(screen.rows.get(0).body.contains("номинал ЦБ: 10"));
    }
    @Test public void selectedStationAndEmptyRadioState()throws Exception {
        JSONObject success=fixture("success");
        JSONObject envelope=new JSONObject().put("meta",success.getJSONObject("meta")).put("data",new JSONObject().put("items",success.getJSONArray("stations")).put("total",3));
        Dtos.Catalogue catalogue=new Dtos.Catalogue(envelope);
        Dtos.Station selected=catalogue.stations.get(1);
        assertEquals(selected.name,Presenters.radio(catalogue,selected.id).rows.get(0).title);
        envelope.getJSONObject("data").put("items",new JSONArray()).put("total",0);
        assertEquals("Станция не выбрана",Presenters.radio(new Dtos.Catalogue(envelope),null).rows.get(0).title);
    }
}

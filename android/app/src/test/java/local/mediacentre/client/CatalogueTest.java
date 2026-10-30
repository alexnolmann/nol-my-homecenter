package local.mediacentre.client;
import java.io.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import local.mediacentre.client.data.Dtos;
public final class CatalogueTest {
    private JSONObject fixture()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream input=getClass().getResourceAsStream("/success.json")){byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1)out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));
    }
    @Test public void pageMetadataAndGenreChoicesDoNotDependOnTheVisiblePage()throws Exception{
        JSONObject f=fixture();JSONObject station=f.getJSONArray("stations").getJSONObject(0);station.put("genres",new JSONArray().put("Jazz").put("Blues"));
        JSONObject data=new JSONObject().put("items",new JSONArray().put(station)).put("total",13).put("limit",5).put("offset",10).put("available_genres",new JSONArray().put("Jazz").put("Blues").put("Rock"));
        Dtos.Catalogue page=new Dtos.Catalogue(new JSONObject().put("meta",f.getJSONObject("meta")).put("data",data));
        assertEquals(13,page.total);assertEquals(5,page.limit);assertEquals(10,page.offset);assertEquals(3,page.availableGenres.size());assertEquals("Blues",page.stations.get(0).genres.get(1));
        data.put("limit",0);try{new Dtos.Catalogue(new JSONObject().put("meta",f.getJSONObject("meta")).put("data",data));fail("Invalid page accepted");}catch(JSONException expected){}
    }
    @Test public void oldStationLabelsRemainReadableWithoutNewFields()throws Exception{
        JSONObject f=fixture();JSONObject station=f.getJSONArray("stations").getJSONObject(0);station.put("genre","Jazz, Blues");
        Dtos.Station old=new Dtos.Station(station);assertEquals(2,old.genres.size());assertEquals("Jazz",old.genres.get(0));
    }
}

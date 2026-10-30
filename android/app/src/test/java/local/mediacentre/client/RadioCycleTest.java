package local.mediacentre.client;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.presentation.RadioCycle;
public final class RadioCycleTest {
    private Dtos.Station station(String id,boolean enabled)throws Exception{return new Dtos.Station(new JSONObject().put("id",id).put("name",id).put("genre","music").put("stream_url","https://example.invalid/original").put("enabled",enabled).put("favorite",true));}
    @Test public void cyclingWrapsAndSkipsDisabledEvenIfTheSelectionWasRemoved()throws Exception{
        Dtos.Station first=station("first",true),disabled=station("disabled",false),last=station("last",true);List<Dtos.Station> stations=Arrays.asList(first,disabled,last);
        assertSame(last,RadioCycle.next(stations,"first",1));assertSame(first,RadioCycle.next(stations,"last",1));assertSame(last,RadioCycle.next(stations,"first",-1));
        assertSame(first,RadioCycle.next(stations,"removed",1));assertSame(last,RadioCycle.next(stations,"removed",-1));
        assertNull(RadioCycle.next(Arrays.asList(disabled),"disabled",1));assertNull(RadioCycle.next(Collections.emptyList(),null,1));
    }
}

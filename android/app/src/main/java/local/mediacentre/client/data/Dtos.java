package local.mediacentre.client.data;
import org.json.*;
import java.math.BigDecimal;
import java.util.*;
import local.mediacentre.client.BuildConfig;

/** Known fields only; NULL remains absent and decimals never pass through double. */
public final class Dtos {
    private Dtos(){}
    public static BigDecimal decimal(JSONObject object,String key) throws JSONException {
        if(object.isNull(key))return null;
        Object value=object.get(key);
        if(!(value instanceof String)||((String)value).length()>40||!((String)value).matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?"))throw new JSONException("Expected decimal string.");
        try{return new BigDecimal((String)value);}catch(NumberFormatException error){throw new JSONException("Invalid decimal.");}
    }
    public static String optional(JSONObject object,String key)throws JSONException{return object.isNull(key)?null:object.getString(key);}
    private static boolean expiredSnapshots(Object value,long now)throws JSONException {
        if(value instanceof JSONArray){JSONArray list=(JSONArray)value;for(int n=0;n<list.length();n++)if(expiredSnapshots(list.get(n),now))return true;}
        else if(value instanceof JSONObject){JSONObject object=(JSONObject)value;if(object.has("enabled")&&!object.optBoolean("enabled",true))return false;if(!object.isNull("valid_until")&&PanelRepository.expired(object.optString("valid_until",null),now))return true;Iterator<String> keys=object.keys();while(keys.hasNext())if(expiredSnapshots(object.get(keys.next()),now))return true;}
        return false;
    }
    public static final class Meta {
        public final boolean testData,offline,expired,revalidating;
        public final String scenario,freshness,fetchedAt,validUntil,dataAsOf,source;
        public Meta(JSONObject envelope)throws JSONException{JSONObject m=envelope.getJSONObject("meta");testData=m.getBoolean("test_data");scenario=m.getString("scenario");freshness=m.getString("freshness");fetchedAt=optional(m,"fetched_at");validUntil=optional(m,"valid_until");dataAsOf=optional(m,"data_as_of");source=m.optString("source","—");JSONObject local=envelope.optJSONObject("_client_cache");offline=local!=null&&local.optBoolean("offline",false);revalidating=local!=null&&local.optBoolean("revalidating",false);expired=expiredSnapshots(envelope,System.currentTimeMillis());}
    }
    public static final class Bootstrap {
        public final boolean homeEnabled,radioEnabled,photoframeEnabled;
        public final int radioVolume;
        public final String city,timezone,selectedStation,displayTitle;
        public Bootstrap(JSONObject envelope)throws JSONException{
            JSONObject d=envelope.getJSONObject("data");if(!"1".equals(d.getString("api_version"))||d.getInt("min_client_version")>BuildConfig.VERSION_CODE)throw new JSONException("Unsupported version.");
            homeEnabled=d.getJSONObject("features").getBoolean("home");if(homeEnabled)throw new JSONException("Home must remain disabled.");
            radioEnabled=d.getJSONObject("features").optBoolean("radio",true);
            photoframeEnabled=d.getJSONObject("features").optBoolean("photoframe",false);
            JSONObject s=d.getJSONObject("settings");city=s.getJSONObject("location").getString("name");timezone=s.getJSONObject("location").getString("timezone");selectedStation=optional(s,"selected_station_id");displayTitle=s.optString("display_title","Мой дом");radioVolume=s.getInt("radio_volume");if(radioVolume<0||radioVolume>100)throw new JSONException("Invalid radio volume.");
        }
    }
    public static final class Day {
        public final String date,condition;
        public final BigDecimal minimum,maximum,rain;
        Day(JSONObject d)throws JSONException{date=d.getString("date");condition=d.getString("condition");minimum=decimal(d,"temp_min_c");maximum=decimal(d,"temp_max_c");rain=decimal(d,"precipitation_mm");}
    }
    public static final class WeatherPeriod {
        public final String at,condition;
        public final BigDecimal temperature;
        WeatherPeriod(JSONObject d)throws JSONException{at=d.getString("at");condition=d.getString("condition");temperature=decimal(d,"temperature_c");}
    }
    public static final class Weather {
        public final Meta meta;
        public final String city,timezone,condition,attribution;
        public final BigDecimal temperature,apparent,humidity,wind;
        public final List<Day> days=new ArrayList<>();
        public final List<WeatherPeriod> periods=new ArrayList<>();
        public Weather(JSONObject envelope)throws JSONException{meta=new Meta(envelope);JSONObject d=envelope.getJSONObject("data");city=d.getJSONObject("location").getString("name");timezone=d.getJSONObject("location").getString("timezone");attribution=d.optString("attribution",meta.source);JSONObject current=d.getJSONObject("current");temperature=decimal(current,"temperature_c");apparent=decimal(current,"apparent_temperature_c");humidity=decimal(current,"humidity_percent");wind=decimal(current,"wind_speed_ms");condition=current.getString("condition");JSONArray a=d.getJSONArray("days");if(a.length()>14)throw new JSONException("Too many forecast days.");for(int i=0;i<a.length();i++)days.add(new Day(a.getJSONObject(i)));JSONArray p=d.optJSONArray("periods");if(p!=null){if(p.length()>6)throw new JSONException("Too many weather periods.");for(int i=0;i<p.length();i++)periods.add(new WeatherPeriod(p.getJSONObject(i)));}}
    }
    public static final class Instrument {
        public final String symbol,name,kind,board,source,rateDate,tradeAt,fetchedAt,validUntil,dataAsOf,status,accessMode,comparison,referenceDate,priceKind,referenceError,lastError,marketState;
        public final boolean enabled;
        public final BigDecimal price,previousPrice,nominal,change;
        public final Integer delay;
        Instrument(JSONObject d)throws JSONException{symbol=d.getString("symbol");name=d.optString("name",symbol);enabled=d.optBoolean("enabled",true);kind=d.getString("kind");board=optional(d,"board_id");source=d.getString("source");rateDate=optional(d,"rate_date");tradeAt=optional(d,"trade_at");fetchedAt=optional(d,"fetched_at");validUntil=optional(d,"valid_until");dataAsOf=optional(d,"data_as_of");status=d.getString("status");accessMode=d.optString("access_mode","unknown");comparison=d.optString("comparison","none");referenceDate=optional(d,"reference_date");priceKind=d.getString("price_kind");referenceError=optional(d,"reference_error");lastError=optional(d,"last_error_code");marketState=d.optString("market_state","unknown");price=decimal(d,"price");previousPrice=decimal(d,"previous_price");nominal=decimal(d,"nominal");change=decimal(d,"change_percent");delay=d.isNull("delay_minutes")?null:d.getInt("delay_minutes");}
    }
    public static final class Finance {
        public final Meta meta;
        public final List<Instrument> items=new ArrayList<>();
        public Finance(JSONObject envelope)throws JSONException{meta=new Meta(envelope);JSONArray a=envelope.getJSONObject("data").getJSONArray("items");if(a.length()>50)throw new JSONException("Watchlist too large.");for(int i=0;i<a.length();i++)items.add(new Instrument(a.getJSONObject(i)));}
    }
    public static final class Station {
        public final String id,name,genre,url;
        public final List<String> genres=new ArrayList<>();
        public final boolean enabled,favorite;
        public Station(JSONObject d)throws JSONException{id=d.getString("id");name=d.getString("name");genre=d.getString("genre");url=d.getString("stream_url");enabled=d.getBoolean("enabled");favorite=d.getBoolean("favorite");JSONArray g=d.optJSONArray("genres");if(g!=null){if(g.length()>10)throw new JSONException("Too many station genres.");for(int n=0;n<g.length();n++)genres.add(g.getString(n));}else for(String label:genre.split(","))if(!label.trim().isEmpty())genres.add(label.trim());}
    }
    public static final class Catalogue {
        public final Meta meta;
        public final int total,limit,offset;
        public final List<String> availableGenres=new ArrayList<>();
        public final List<Station> stations=new ArrayList<>();
        public Catalogue(JSONObject envelope)throws JSONException{meta=new Meta(envelope);JSONObject d=envelope.getJSONObject("data");total=d.getInt("total");limit=d.optInt("limit",50);offset=d.optInt("offset",0);JSONArray a=d.getJSONArray("items");if(total<0||offset<0||limit<1||limit>50||a.length()>limit||a.length()>total)throw new JSONException("Invalid catalogue page.");for(int i=0;i<a.length();i++)stations.add(new Station(a.getJSONObject(i)));JSONArray g=d.optJSONArray("available_genres");if(g!=null){for(int n=0;n<g.length();n++)availableGenres.add(g.getString(n));}else{for(Station station:stations)for(String label:station.genres)if(!availableGenres.contains(label))availableGenres.add(label);}}
        public Catalogue(Catalogue first,List<Station> all){meta=first.meta;total=first.total;limit=first.limit;offset=0;availableGenres.addAll(first.availableGenres);stations.addAll(all);if(stations.size()!=total)throw new IllegalArgumentException("Incomplete station catalogue.");}
    }
    public static final class Dashboard {
        public final Meta meta;
        public final Weather weather;
        public final Finance finance;
        public final List<Station> favorites=new ArrayList<>();
        public Dashboard(JSONObject envelope)throws JSONException{meta=new Meta(envelope);JSONObject d=envelope.getJSONObject("data");weather=new Weather(d.getJSONObject("weather"));finance=new Finance(d.getJSONObject("finance"));JSONArray a=d.getJSONObject("radio").getJSONArray("favorites");for(int i=0;i<a.length();i++)favorites.add(new Station(a.getJSONObject(i)));}
    }
}

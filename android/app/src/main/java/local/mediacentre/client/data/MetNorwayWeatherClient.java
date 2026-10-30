package local.mediacentre.client.data;

import android.content.Context;
import local.mediacentre.client.R;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/** Keyless direct fallback; the endpoint and host are fixed locally. */
final class MetNorwayWeatherClient implements PanelRepository.WeatherSource {
    private static final int MAX_RESPONSE=2*1024*1024;
    private final Context context;
    MetNorwayWeatherClient(Context context){this.context=context.getApplicationContext();}
    @Override public JSONObject fetch(JSONObject location)throws Exception {
        String latitude=DirectWeatherClient.coordinate(location,"latitude",-90,90);
        String longitude=DirectWeatherClient.coordinate(location,"longitude",-180,180);
        String zone=location.getString("timezone");if(!TimeZone.getTimeZone(zone).getID().equals(zone))throw new IOException("Invalid location timezone.");
        String endpoint="https://api.met.no/weatherapi/locationforecast/2.0/compact?lat="+rounded(latitude)+"&lon="+rounded(longitude);
        HttpsURLConnection connection=(HttpsURLConnection)new URL(endpoint).openConnection();
        connection.setSSLSocketFactory(new Tls12SocketFactory(trustedFactory()));
        connection.setConnectTimeout(4000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("User-Agent","MediaCentre/0.8 (+https://github.com/alexnolmann/nol-my-homecenter)");
        try {
            if(connection.getResponseCode()!=200)throw new IOException("Weather source HTTP "+connection.getResponseCode());
            if(connection.getContentLength()>MAX_RESPONSE)throw new IOException("Weather response too large.");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] block=new byte[8192];int count;
                while((count=input.read(block))!=-1){if(output.size()+count>MAX_RESPONSE)throw new IOException("Weather response too large.");output.write(block,0,count);}
                return normalize(new JSONObject(output.toString("UTF-8")),location,System.currentTimeMillis(),connection.getHeaderField("Expires"));
            }
        }finally{connection.disconnect();}
    }
    private SSLSocketFactory trustedFactory()throws Exception {
        CertificateFactory certificates=CertificateFactory.getInstance("X.509");Certificate root;
        try(InputStream source=context.getResources().openRawResource(R.raw.harica_tls_rsa_root_2021)){root=certificates.generateCertificate(source);}
        KeyStore anchors=KeyStore.getInstance(KeyStore.getDefaultType());anchors.load(null,null);anchors.setCertificateEntry("harica-rsa-2021",root);
        TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(anchors);
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,trust.getTrustManagers(),null);return tls.getSocketFactory();
    }
    private static String rounded(String coordinate){return new BigDecimal(coordinate).setScale(4,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
    static JSONObject normalize(JSONObject raw,JSONObject location,long fetchedAt,String expires)throws Exception {
        long now=fetchedAt/1000;TimeZone zone=TimeZone.getTimeZone(location.getString("timezone"));
        if(!zone.getID().equals(location.getString("timezone")))throw new IOException("Invalid location timezone.");
        JSONObject properties=raw.getJSONObject("properties"),metaSource=properties.getJSONObject("meta"),units=metaSource.getJSONObject("units");
        for(String[] unit:new String[][]{{"air_temperature","celsius"},{"relative_humidity","%"},{"wind_speed","m/s"},{"precipitation_amount","mm"}})
            if(!unit[1].equals(units.optString(unit[0])))throw new IOException("Weather units changed.");
        long updated=parse(metaSource.getString("updated_at"));if(updated>now+300||updated<now-86400)throw new IOException("Invalid weather update time.");
        JSONArray series=properties.getJSONArray("timeseries");if(series.length()<24||series.length()>400)throw new IOException("Invalid weather timeline.");
        JSONObject currentEntry=null;long currentAt=0,previous=Long.MIN_VALUE;JSONArray periods=new JSONArray();
        java.util.LinkedHashMap<String,Day> daily=new java.util.LinkedHashMap<>();
        SimpleDateFormat date=formatter("yyyy-MM-dd",zone);Calendar hour=Calendar.getInstance(zone);
        for(int i=0;i<series.length();i++){
            JSONObject entry=series.getJSONObject(i),data=entry.getJSONObject("data");long at=parse(entry.getString("time"));
            if(at<=previous)throw new IOException("Weather timeline is unordered.");previous=at;
            JSONObject instant=data.getJSONObject("instant").getJSONObject("details");
            Object temp=decimal(instant.opt("air_temperature"),-100,70);String symbol=symbol(data);String condition=condition(symbol);
            if(at<=now){currentEntry=entry;currentAt=at;}else if(currentEntry==null){currentEntry=entry;currentAt=at;}
            String local=date.format(new Date(at*1000));Day day=daily.get(local);if(day==null){day=new Day(local);daily.put(local,day);}
            if(temp instanceof String){double value=Double.parseDouble((String)temp);day.min=Math.min(day.min,value);day.max=Math.max(day.max,value);}
            hour.setTimeInMillis(at*1000);if(!"unknown".equals(condition)&&("unknown".equals(day.condition)||hour.get(Calendar.HOUR_OF_DAY)==12))day.condition=condition;
            JSONObject next=data.optJSONObject("next_1_hours");if(next==null)next=data.optJSONObject("next_6_hours");JSONObject detail=next==null?null:next.optJSONObject("details");
            if(detail!=null&&!detail.isNull("precipitation_amount")&&detail.has("precipitation_amount")){day.precip+=Double.parseDouble((String)decimal(detail.get("precipitation_amount"),0,3000));day.hasPrecip=true;}
            if(at>now&&periods.length()<6&&hour.get(Calendar.HOUR_OF_DAY)%6==0)periods.put(new JSONObject().put("at",iso(at*1000)).put("temperature_c",temp).put("condition",condition));
        }
        if(currentEntry==null||currentAt<now-21600||currentAt>now+21600)throw new IOException("Current weather unavailable.");
        JSONArray days=new JSONArray();Calendar expected=Calendar.getInstance(zone);expected.setTimeInMillis(fetchedAt);
        for(int i=0;i<14;i++){
            String key=date.format(expected.getTime());Day day=daily.get(key);if(day==null)break;
            days.put(new JSONObject().put("date",key).put("temp_min_c",Double.isInfinite(day.min)?JSONObject.NULL:decimal(day.min,-100,70))
                .put("temp_max_c",Double.isInfinite(day.max)?JSONObject.NULL:decimal(day.max,-100,70))
                .put("precipitation_mm",day.hasPrecip?decimal(day.precip,0,3000):JSONObject.NULL).put("condition",day.condition).put("weather_code",JSONObject.NULL));
            expected.add(Calendar.DATE,1);
        }
        if(days.length()<7)throw new IOException("Forecast too short.");
        JSONObject currentData=currentEntry.getJSONObject("data"),details=currentData.getJSONObject("instant").getJSONObject("details");
        JSONObject current=new JSONObject().put("temperature_c",decimal(details.opt("air_temperature"),-100,70)).put("condition",condition(symbol(currentData)))
            .put("weather_code",JSONObject.NULL).put("apparent_temperature_c",JSONObject.NULL).put("humidity_percent",decimal(details.opt("relative_humidity"),0,100))
            .put("wind_speed_ms",decimal(details.opt("wind_speed"),0,200)).put("source_time",iso(currentAt*1000));
        long valid=now+1800;if(expires!=null)try{SimpleDateFormat http=formatter("EEE, dd MMM yyyy HH:mm:ss 'GMT'",TimeZone.getTimeZone("UTC"));http.setLenient(false);Date parsed=http.parse(expires);if(parsed!=null)valid=Math.max(now,Math.min(now+86400,parsed.getTime()/1000));}catch(Exception ignored){}
        JSONObject data=new JSONObject().put("location",new JSONObject(location.toString())).put("current",current).put("days",days).put("periods",periods).put("attribution","MET Norway · CC BY 4.0");
        JSONObject meta=new JSONObject().put("test_data",false).put("scenario","database").put("revision",1).put("freshness","fresh").put("data_as_of",iso(updated*1000))
            .put("fetched_at",iso(fetchedAt)).put("valid_until",iso(valid*1000)).put("source","MET Norway · планшет");
        JSONObject result=new JSONObject().put("data",data).put("meta",meta);new Dtos.Weather(result);return result;
    }
    private static final class Day {final String date;double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY,precip;boolean hasPrecip;String condition="unknown";Day(String date){this.date=date;}}
    private static String symbol(JSONObject data){JSONObject one=data.optJSONObject("next_1_hours"),six=data.optJSONObject("next_6_hours");JSONObject summary=one==null?null:one.optJSONObject("summary");if(summary==null)summary=six==null?null:six.optJSONObject("summary");return summary==null?null:summary.optString("symbol_code",null);}
    private static String condition(String symbol){if(symbol==null)return "unknown";symbol=symbol.toLowerCase(Locale.US);if(symbol.contains("thunder"))return "thunderstorm";if(symbol.contains("sleet")||symbol.contains("freezing"))return "freezing_rain";if(symbol.contains("snow"))return "snow";if(symbol.contains("drizzle"))return "drizzle";if(symbol.contains("rain")||symbol.contains("showers"))return "rain";if(symbol.contains("fog"))return "fog";if(symbol.contains("partly")||symbol.contains("fair"))return "partly_cloudy";if(symbol.contains("cloud"))return "cloudy";if(symbol.contains("clear")||symbol.contains("sunny"))return "clear";return "unknown";}
    private static Object decimal(Object value,int min,int max)throws IOException {if(value==null||value==JSONObject.NULL)return JSONObject.NULL;if(!(value instanceof Number))throw new IOException("Invalid weather number.");BigDecimal number=new BigDecimal(value.toString());if(number.compareTo(BigDecimal.valueOf(min))<0||number.compareTo(BigDecimal.valueOf(max))>0)throw new IOException("Weather number out of range.");return number.setScale(3,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
    private static long parse(String value)throws IOException {SimpleDateFormat format=formatter("yyyy-MM-dd'T'HH:mm:ss'Z'",TimeZone.getTimeZone("UTC"));ParsePosition position=new ParsePosition(0);Date date=format.parse(value,position);if(date==null||position.getIndex()!=value.length())throw new IOException("Invalid weather time.");return date.getTime()/1000;}
    private static SimpleDateFormat formatter(String pattern,TimeZone zone){SimpleDateFormat value=new SimpleDateFormat(pattern,Locale.US);value.setTimeZone(zone);value.setLenient(false);return value;}
    private static String iso(long time){return formatter("yyyy-MM-dd'T'HH:mm:ss'Z'",TimeZone.getTimeZone("UTC")).format(new Date(time));}
}

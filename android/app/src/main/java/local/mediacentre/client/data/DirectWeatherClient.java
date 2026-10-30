package local.mediacentre.client.data;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/** Fixed, HTTPS-only fallback for the configured location; never accepts a provider URL from the server. */
final class DirectWeatherClient implements PanelRepository.WeatherSource {
    private static final int MAX_RESPONSE=2*1024*1024;
    @Override public JSONObject fetch(JSONObject location)throws Exception {
        String latitude=coordinate(location,"latitude",-90,90),longitude=coordinate(location,"longitude",-180,180);
        String zone=location.getString("timezone");
        if(!TimeZone.getTimeZone(zone).getID().equals(zone))throw new IOException("Invalid location timezone.");
        String url="https://api.open-meteo.com/v1/forecast?latitude="+latitude+"&longitude="+longitude+"&timezone="+URLEncoder.encode(zone,"UTF-8")
            +"&forecast_days=14&timeformat=unixtime&temperature_unit=celsius&wind_speed_unit=ms&precipitation_unit=mm"
            +"&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m"
            +"&daily=weather_code,temperature_2m_min,temperature_2m_max,precipitation_sum&hourly=temperature_2m,weather_code";
        HttpsURLConnection connection=(HttpsURLConnection)new URL(url).openConnection();
        connection.setSSLSocketFactory(new Tls12SocketFactory((SSLSocketFactory)SSLSocketFactory.getDefault()));
        connection.setConnectTimeout(4000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);connection.setRequestProperty("Accept","application/json");
        try {
            if(connection.getResponseCode()!=200)throw new IOException("Weather source HTTP "+connection.getResponseCode());
            if(connection.getContentLength()>MAX_RESPONSE)throw new IOException("Weather response too large.");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] block=new byte[8192];int count;
                while((count=input.read(block))!=-1){if(output.size()+count>MAX_RESPONSE)throw new IOException("Weather response too large.");output.write(block,0,count);}
                return normalize(new JSONObject(output.toString("UTF-8")),location,System.currentTimeMillis());
            }
        }finally{connection.disconnect();}
    }
    static String coordinate(JSONObject location,String key,int min,int max)throws Exception {
        Object value=location.get(key);if(!(value instanceof String)||!((String)value).matches("-?(0|[1-9][0-9]*)(\\.[0-9]{1,6})?"))throw new IOException("Invalid location coordinate.");
        BigDecimal decimal=new BigDecimal((String)value);if(decimal.compareTo(BigDecimal.valueOf(min))<0||decimal.compareTo(BigDecimal.valueOf(max))>0)throw new IOException("Location coordinate out of range.");
        return decimal.toPlainString();
    }
    static JSONObject normalize(JSONObject raw,JSONObject location,long fetchedAt)throws Exception {
        String zone=location.getString("timezone");TimeZone timezone=TimeZone.getTimeZone(zone);
        if(!timezone.getID().equals(zone)||!zone.equals(raw.getString("timezone")))throw new IOException("Weather timezone mismatch.");
        int offset=raw.getInt("utc_offset_seconds");if(Math.abs(offset)>50400)throw new IOException("Weather offset out of range.");
        JSONObject currentUnits=raw.getJSONObject("current_units"),dailyUnits=raw.getJSONObject("daily_units");
        units(currentUnits,"time","unixtime");units(currentUnits,"temperature_2m","°C");units(currentUnits,"apparent_temperature","°C");units(currentUnits,"relative_humidity_2m","%");units(currentUnits,"wind_speed_10m","m/s");
        units(dailyUnits,"time","unixtime");units(dailyUnits,"temperature_2m_min","°C");units(dailyUnits,"temperature_2m_max","°C");units(dailyUnits,"precipitation_sum","mm");
        JSONObject current=raw.getJSONObject("current");long observed=current.getLong("time");long now=fetchedAt/1000;
        if(observed>now+300||observed<now-86400)throw new IOException("Weather observation outside current day.");
        String fetched=iso(fetchedAt),asOf=iso(observed*1000);
        JSONObject daily=raw.getJSONObject("daily");JSONArray times=daily.getJSONArray("time"),codes=daily.getJSONArray("weather_code"),minimum=daily.getJSONArray("temperature_2m_min"),maximum=daily.getJSONArray("temperature_2m_max"),rain=daily.getJSONArray("precipitation_sum");
        int count=times.length();if((count!=7&&count!=14)||codes.length()!=count||minimum.length()!=count||maximum.length()!=count||rain.length()!=count)throw new IOException("Weather days are incomplete.");
        SimpleDateFormat localDate=format("yyyy-MM-dd",timezone),utcDate=format("yyyy-MM-dd",TimeZone.getTimeZone("UTC"));Calendar expected=Calendar.getInstance(timezone);expected.setTimeInMillis(observed*1000);
        JSONArray days=new JSONArray();
        for(int i=0;i<count;i++){
            long at=times.getLong(i);String actual=utcDate.format(new Date((at+offset)*1000));String wanted=localDate.format(expected.getTime());
            if(!actual.equals(wanted))throw new IOException("Weather dates are not consecutive.");expected.add(Calendar.DATE,1);
            Object low=decimal(minimum.get(i),-100,70),high=decimal(maximum.get(i),-100,70);
            if(low instanceof String&&high instanceof String&&new BigDecimal((String)low).compareTo(new BigDecimal((String)high))>0)throw new IOException("Weather minimum exceeds maximum.");
            days.put(new JSONObject().put("date",actual).put("temp_min_c",low).put("temp_max_c",high).put("precipitation_mm",decimal(rain.get(i),0,3000)).put("condition",condition(codes.get(i))).put("weather_code",codes.get(i)));
        }
        JSONArray periods=new JSONArray();JSONObject hourly=raw.optJSONObject("hourly");
        if(hourly!=null){
            JSONObject hourlyUnits=raw.getJSONObject("hourly_units");units(hourlyUnits,"time","unixtime");units(hourlyUnits,"temperature_2m","°C");
            JSONArray hours=hourly.getJSONArray("time"),hourlyTemps=hourly.getJSONArray("temperature_2m"),hourlyCodes=hourly.getJSONArray("weather_code");
            if(hours.length()!=count*24||hourlyTemps.length()!=hours.length()||hourlyCodes.length()!=hours.length())throw new IOException("Weather hours are incomplete.");
            long previous=Long.MIN_VALUE;Calendar localHour=Calendar.getInstance(timezone);
            for(int i=0;i<hours.length();i++){
                long at=hours.getLong(i);if(previous!=Long.MIN_VALUE&&at-previous!=3600)throw new IOException("Weather hours are not consecutive.");previous=at;
                if(at<=observed||periods.length()>=6)continue;
                localHour.setTimeInMillis(at*1000);if(localHour.get(Calendar.HOUR_OF_DAY)%6!=0)continue;
                periods.put(new JSONObject().put("at",iso(at*1000)).put("temperature_c",decimal(hourlyTemps.get(i),-100,70)).put("condition",condition(hourlyCodes.get(i))));
            }
        }
        JSONObject normalizedCurrent=new JSONObject().put("temperature_c",decimal(current.opt("temperature_2m"),-100,70)).put("condition",condition(current.opt("weather_code")))
            .put("weather_code",current.opt("weather_code")).put("apparent_temperature_c",decimal(current.opt("apparent_temperature"),-120,90))
            .put("humidity_percent",decimal(current.opt("relative_humidity_2m"),0,100)).put("wind_speed_ms",decimal(current.opt("wind_speed_10m"),0,200)).put("source_time",asOf);
        JSONObject data=new JSONObject().put("location",new JSONObject(location.toString())).put("current",normalizedCurrent).put("days",days).put("periods",periods).put("attribution","Open-Meteo.com");
        JSONObject meta=new JSONObject().put("test_data",false).put("scenario","database").put("revision",1).put("freshness","fresh").put("data_as_of",asOf)
            .put("fetched_at",fetched).put("valid_until",iso(fetchedAt+30*60*1000)).put("source","Open-Meteo · планшет");
        JSONObject result=new JSONObject().put("data",data).put("meta",meta);new Dtos.Weather(result);return result;
    }
    private static void units(JSONObject object,String key,String expected)throws Exception {if(!expected.equals(object.getString(key)))throw new IOException("Weather units changed.");}
    private static Object decimal(Object value,int min,int max)throws Exception {
        if(value==null||value==JSONObject.NULL)return JSONObject.NULL;
        if(!(value instanceof Number))throw new IOException("Invalid weather number.");
        BigDecimal number=new BigDecimal(value.toString());if(number.compareTo(BigDecimal.valueOf(min))<0||number.compareTo(BigDecimal.valueOf(max))>0)throw new IOException("Weather number out of range.");
        return number.setScale(3,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    private static String condition(Object value)throws Exception {
        if(value==null||value==JSONObject.NULL)return "unknown";
        if(!(value instanceof Number))throw new IOException("Invalid weather code.");int code=((Number)value).intValue();if(code<0||code>999||new BigDecimal(value.toString()).compareTo(BigDecimal.valueOf(code))!=0)throw new IOException("Invalid weather code.");
        switch(code){case 0:return "clear";case 1:case 2:return "partly_cloudy";case 3:return "cloudy";case 45:case 48:return "fog";case 51:case 53:case 55:return "drizzle";case 56:case 57:case 66:case 67:return "freezing_rain";case 61:case 63:case 65:case 80:case 81:case 82:return "rain";case 71:case 73:case 75:case 77:case 85:case 86:return "snow";case 95:case 96:case 97:case 99:return "thunderstorm";default:return "unknown";}
    }
    private static SimpleDateFormat format(String pattern,TimeZone zone){SimpleDateFormat value=new SimpleDateFormat(pattern,Locale.US);value.setTimeZone(zone);value.setLenient(false);return value;}
    private static String iso(long time){return format("yyyy-MM-dd'T'HH:mm:ss'Z'",TimeZone.getTimeZone("UTC")).format(new Date(time));}
}

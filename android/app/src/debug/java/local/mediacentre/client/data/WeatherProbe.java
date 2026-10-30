package local.mediacentre.client.data;

import android.util.Log;
import android.content.Context;
import org.json.JSONObject;

/** On-device HTTPS compatibility check for the weather fallback. */
public final class WeatherProbe {
    private WeatherProbe(){}
    public static void run(Context context){
        try{
            JSONObject location=new JSONObject().put("id","00000000-0000-4000-8000-000000000001")
                .put("name","Санкт-Петербург").put("timezone","Europe/Moscow").put("revision",1)
                .put("latitude","59.938630").put("longitude","30.314130");
            JSONObject forecast=new MetNorwayWeatherClient(context).fetch(location);
            Log.i("MediaCentreWeather","WEATHER_DIRECT_PROBE_OK days="+forecast.getJSONObject("data").getJSONArray("days").length()
                +" periods="+forecast.getJSONObject("data").getJSONArray("periods").length());
        }catch(Exception error){Log.e("MediaCentreWeather","WEATHER_DIRECT_PROBE_ERROR",error);}
    }
}

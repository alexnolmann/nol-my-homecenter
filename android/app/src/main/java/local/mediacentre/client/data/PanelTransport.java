package local.mediacentre.client.data;
import org.json.JSONObject;

/** The repository owns cache policy; the transport owns authenticated HTTP/HTTPS. */
public interface PanelTransport {
    ApiClient.Response fetch(String path,String etag)throws Exception;
    JSONObject favorite(String id,boolean enabled,String scenario)throws Exception;
    JSONObject preferences(String path,JSONObject body,String match)throws Exception;
    JSONObject heartbeat(JSONObject data,String scenario)throws Exception;
    void refreshWeather()throws Exception;
}

package local.mediacentre.client.data;
import org.json.*;
import java.io.IOException;
import java.util.Locale;
/** Strict pairing result; credentials never become UI text or logs. */
public final class PairIdentity {
    public final String token,serverId,clientId;
    public static final class ServerChanged extends IOException {public ServerChanged(){super("Сервер изменился. Требуется новый код сопряжения.");}}
    public PairIdentity(JSONObject envelope)throws JSONException {
        JSONObject data=envelope.getJSONObject("data");token=data.getString("token");serverId=data.getString("server_id");clientId=data.getString("client_id");
        if(!token.matches("[a-f0-9]{64}") || !uuid(serverId) || !uuid(clientId))throw new JSONException("Invalid paired identity.");
        JSONArray scopes=data.getJSONArray("scopes");
        for(int i=0;i<scopes.length();i++)if(!scopes.getString(i).matches("weather:read|finance:read|radio:read|favorites:write|preferences:read|preferences:write|heartbeat:write"))throw new JSONException("Unsupported paired scope.");
    }
    private static boolean uuid(String value){return value.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}");}
    public static String code(String value)throws IOException {
        String normalized=value.trim().toUpperCase(Locale.US);
        if(!normalized.matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}"))throw new IOException("Введите восьмисимвольный код из панели.");return normalized;
    }
    public static void verifyServer(String expected,String actual)throws ServerChanged {if(expected!=null && !expected.equals(actual))throw new ServerChanged();}
}

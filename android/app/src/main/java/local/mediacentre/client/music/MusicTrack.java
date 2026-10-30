package local.mediacentre.client.music;
import org.json.JSONObject;
import java.io.IOException;
public final class MusicTrack {
    public final String id,name,artist,album,url;
    public final long bytes;
    public MusicTrack(JSONObject row)throws Exception {
        id=row.getString("id");name=row.getString("name");artist=row.isNull("artist")?"":row.optString("artist","");album=row.isNull("album")?"":row.optString("album","");url=row.getString("url");bytes=row.getLong("bytes");
        String uuid="[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}";
        if(!id.matches(uuid)||!url.matches("/api/v1/music/stream/"+uuid+"/[1-9][0-9]{0,17}/"+id)||name.length()>255||artist.length()>255||album.length()>255||bytes<1)throw new IOException("Invalid music track.");
    }
}

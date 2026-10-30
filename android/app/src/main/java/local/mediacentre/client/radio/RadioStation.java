package local.mediacentre.client.radio;
import java.net.URI;
/** Only catalogue identity and original public URL cross the playback boundary. */
public final class RadioStation {
    public final String id,name,url;
    public RadioStation(String id,String name,String url){
        if(id==null || !id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}") || name==null || name.trim().isEmpty() || name.length()>255 || url==null || url.length()>2048)throw new IllegalArgumentException("Invalid station.");
        try{URI u=new URI(url);if(!("http".equals(u.getScheme()) || "https".equals(u.getScheme())) || u.getHost()==null || u.getRawUserInfo()!=null || u.getRawFragment()!=null || u.getPort()==0 || u.getPort()>65535)throw new IllegalArgumentException("Public HTTP stream required.");}
        catch(java.net.URISyntaxException error){throw new IllegalArgumentException("Invalid stream URL.");}
        this.id=id;this.name=name;this.url=url;
    }
}

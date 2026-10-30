package local.mediacentre.client.data;

import android.content.Context;
import android.os.Build;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import local.mediacentre.client.BuildConfig;

public final class ApiClient implements PanelTransport {
    private final String base,token;
    private final String expectedServer;
    private final SSLSocketFactory sockets;
    public static final class Response {
        public final int code;public final String body,etag;
        public Response(int code,String body,String etag){this.code=code;this.body=body;this.etag=etag;}
    }
    public static final class HttpError extends IOException {
        public final int code;
        public HttpError(int code){super("API HTTP "+code);this.code=code;}
    }
    public ApiClient(Context context,String base,String token) throws Exception {
        this(context,base,token,null);
    }
    public ApiClient(Context context,String base,String token,String expectedServer) throws Exception {
        URL url=new URL(base);
        try { url.toURI(); } catch(java.net.URISyntaxException invalid) { throw new IOException("Некорректный адрес сервера.",invalid); }
        if (!("https".equals(url.getProtocol()) || "http".equals(url.getProtocol())) || url.getPort()==0 || url.getPort()>65535 || url.getUserInfo()!=null || url.getQuery()!=null || url.getRef()!=null || !(url.getPath().isEmpty() || "/".equals(url.getPath()))) throw new IOException("Укажите HTTP- или HTTPS-адрес сервера без пути и параметров.");
        if(url.getHost().isEmpty())throw new IOException("Укажите имя сервера.");
        if(token!=null && token.trim().length()<32)throw new IOException("Нужен токен сопряжения.");
        this.base=base.replaceAll("/+$",""); this.token=token==null?null:token.trim();
        this.expectedServer=expectedServer;
        if ("http".equals(url.getProtocol())) { sockets=null; return; }
        TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init((KeyStore)null);
        if (BuildConfig.DEBUG) {
            InputStream input=null;
            try {input=context.getAssets().open("development-ca.pem");} catch (IOException absent) { /* Standard platform CA trust. */ }
            if (input!=null) try (InputStream certificate=input) {
                KeyStore store=KeyStore.getInstance(KeyStore.getDefaultType()); store.load(null,null);
                store.setCertificateEntry("development",CertificateFactory.getInstance("X.509").generateCertificate(certificate));
                trust.init(store);
            }
        }
        SSLContext ssl=SSLContext.getInstance("TLS"); ssl.init(null,trust.getTrustManagers(),null);
        sockets=new Tls12SocketFactory(ssl.getSocketFactory());
    }
    private HttpURLConnection open(String path)throws IOException {
        HttpURLConnection connection=(HttpURLConnection)new URL(base+path).openConnection();
        if(connection instanceof HttpsURLConnection) ((HttpsURLConnection)connection).setSSLSocketFactory(sockets);
        return connection;
    }
    public static String encode(String value) throws IOException {return URLEncoder.encode(value,"UTF-8");}
    public Response fetch(String path,String etag) throws Exception {return request("GET",path,null,etag);}
    /** Binary photos bypass the small JSON response limit and use the configured server transport. */
    public byte[] photo(String path)throws Exception {
        if(!path.matches("/api/v1/plugins/photoframe/photos/[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}/[a-f0-9]{64}"))throw new IOException("Invalid photo path.");
        if(token==null)throw new IOException("Сначала подключите планшет.");
        HttpURLConnection connection=open(path);
        connection.setConnectTimeout(3000);connection.setReadTimeout(15000);
        connection.setUseCaches(false);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept","image/jpeg");
        connection.setRequestProperty("Authorization","Bearer "+token);
        if(expectedServer!=null)connection.setRequestProperty("X-MediaCentre-Server-Id",expectedServer);
        try{
            int status=connection.getResponseCode();if(status!=200)throw new HttpError(status);
            if(!"image/jpeg".equals(connection.getContentType())||connection.getContentLength()>12*1024*1024)throw new IOException("Invalid photo response.");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(output.size()+count>12*1024*1024)throw new IOException("Photo too large.");output.write(buffer,0,count);}return output.toByteArray();
            }
        }finally{connection.disconnect();}
    }
    /** Incremental audio connection; caller owns/disconnects it. Never follows redirects with credentials. */
    public HttpURLConnection openMusic(String path,String range)throws IOException {
        String uuid="[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}";
        if(!path.matches("/api/v1/music/stream/"+uuid+"/[1-9][0-9]{0,17}/"+uuid)||token==null)throw new IOException("Invalid music source.");
        if(range!=null&&!range.matches("bytes=[0-9]{1,18}-[0-9]{0,18}"))throw new IOException("Invalid music range.");
        HttpURLConnection c=open(path);
        c.setConnectTimeout(3000);c.setReadTimeout(15000);c.setUseCaches(false);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","audio/mpeg");c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("Authorization","Bearer "+token);
        if(expectedServer!=null)c.setRequestProperty("X-MediaCentre-Server-Id",expectedServer);
        if(range!=null)c.setRequestProperty("Range",range);return c;
    }
    public JSONObject favorite(String id,boolean enabled,String scenario)throws Exception {
        if(!id.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))throw new IOException("Invalid station ID.");
        Response response=request(enabled?"PUT":"DELETE","/api/v1/radio/favorites/"+id+"?scenario="+encode(scenario),null,null);
        if(response.code!=200)throw new HttpError(response.code);return new JSONObject(response.body);
    }
    public JSONObject preferences(String path,JSONObject body,String match)throws Exception {
        if(match==null)throw new IOException("Current settings revision required.");
        Response response=request(Build.VERSION.SDK_INT<21?"POST":"PATCH",path,body.toString(),null,match);if(response.code!=200)throw new HttpError(response.code);return new JSONObject(response.body);
    }
    public JSONObject heartbeat(JSONObject data,String scenario) throws Exception {Response response=request("POST","/api/v1/client/heartbeat?scenario="+encode(scenario),data.toString(),null);if(response.code!=200)throw new HttpError(response.code);return new JSONObject(response.body);}
    public JSONObject climate(JSONObject configuration)throws Exception {
        Response response=configuration==null?request("GET","/api/v1/client/climate",null,null):request("POST","/api/v1/client/climate/config",configuration.toString(),null);
        if(response.code!=200)throw new HttpError(response.code);return new JSONObject(response.body);
    }
    public void climateReport(JSONObject samples)throws Exception {
        Response response=request("POST","/api/v1/client/climate/report",samples.toString(),null);if(response.code!=200)throw new HttpError(response.code);
    }
    public void refreshWeather()throws Exception {Response response=request("POST","/api/v1/weather/refresh",null,null);if(response.code!=202)throw new HttpError(response.code);}
    public PairIdentity pair(String code,String name)throws Exception {
        JSONObject input=new JSONObject().put("code",PairIdentity.code(code)).put("client_name",name).put("client_version",BuildConfig.VERSION_NAME);
        Response response=request("POST","/api/v1/pair",input.toString(),null);
        if(response.code!=200)throw new HttpError(response.code);return new PairIdentity(new JSONObject(response.body));
    }
    private Response request(String method,String path,String payload,String etag) throws Exception {
        return request(method,path,payload,etag,null);
    }
    private Response request(String method,String path,String payload,String etag,String match) throws Exception {
        if (!path.startsWith("/api/v1/") || path.startsWith("//")) throw new IOException("Invalid API path.");
        HttpURLConnection connection=open(path);
        connection.setConnectTimeout(3000); connection.setReadTimeout(8000);
        connection.setUseCaches(false); connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method); connection.setRequestProperty("Accept","application/json");
        if(!path.equals("/api/v1/pair")){if(token==null)throw new IOException("Сначала подключите планшет по коду.");connection.setRequestProperty("Authorization","Bearer "+token);if(expectedServer!=null)connection.setRequestProperty("X-MediaCentre-Server-Id",expectedServer);}
        if ("GET".equals(method) && etag!=null) connection.setRequestProperty("If-None-Match",etag);
        if(match!=null)connection.setRequestProperty("If-Match",match);
        try {
            if (payload!=null) {
                byte[] bytes=payload.getBytes("UTF-8"); connection.setDoOutput(true); connection.setFixedLengthStreamingMode(bytes.length);
                connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
                try(OutputStream output=connection.getOutputStream()){output.write(bytes);}
            }
            int code=connection.getResponseCode();
            if(code!=200 && code!=202)return new Response(code,null,connection.getHeaderField("ETag"));
            try(InputStream input=connection.getInputStream()) {
                return new Response(code,BoundedBody.read(input),connection.getHeaderField("ETag"));
            }
        } finally {connection.disconnect();}
    }
}

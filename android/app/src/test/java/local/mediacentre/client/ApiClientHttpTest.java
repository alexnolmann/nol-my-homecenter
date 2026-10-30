package local.mediacentre.client;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import local.mediacentre.client.data.ApiClient;
import local.mediacentre.client.music.MusicStreamBridge;
import org.junit.Test;
import static org.junit.Assert.*;

public class ApiClientHttpTest {
    private interface Handler {void handle(Exchange exchange)throws IOException;}
    private static class Headers extends HashMap<String,String> {
        String getFirst(String key){return get(key.toLowerCase(Locale.US));}
        void set(String key,String value){put(key.toLowerCase(Locale.US),value);}
    }
    private static class Exchange {
        final Socket socket;final InputStream input;final Headers request=new Headers(),response=new Headers();
        final String method,path;
        Exchange(Socket socket)throws IOException {
            this.socket=socket;input=socket.getInputStream();String[] first=line(input).split(" ");method=first[0];path=first[1];
            for(int i=0;i<50;i++){String header=line(input);if(header.isEmpty())break;int colon=header.indexOf(':');if(colon>0)request.set(header.substring(0,colon),header.substring(colon+1).trim());}
        }
        static String line(InputStream input)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();int n;while((n=input.read())!=-1 && n!='\n'){if(n!='\r')out.write(n);if(out.size()>8192)throw new IOException("Large header");}return out.toString("UTF-8");}
        URI getRequestURI(){return URI.create(path);}String getRequestMethod(){return method;}
        Headers getRequestHeaders(){return request;}Headers getResponseHeaders(){return response;}
        InputStream getRequestBody(){
            int length=Integer.parseInt(Optional.ofNullable(request.getFirst("Content-Length")).orElse("0"));
            return new FilterInputStream(input){int remaining=length;public int read()throws IOException{if(remaining<=0)return -1;remaining--;return super.read();}public int read(byte[] b,int off,int len)throws IOException{if(remaining<=0)return -1;int n=in.read(b,off,Math.min(len,remaining));if(n>0)remaining-=n;return n;}};
        }
        OutputStream getResponseBody()throws IOException{return socket.getOutputStream();}
        void sendResponseHeaders(int status,long length)throws IOException {
            StringBuilder headers=new StringBuilder("HTTP/1.1 "+status+" Test\r\nConnection: close\r\nContent-Length: "+Math.max(0,length)+"\r\n");
            for(Map.Entry<String,String> entry:response.entrySet())headers.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            getResponseBody().write(headers.append("\r\n").toString().getBytes("US-ASCII"));
        }
        void close()throws IOException{socket.close();}
    }
    // Real loopback HTTP without Android mocks or optional JDK server modules.
    private static class HttpServer {
        final ServerSocket socket;final Map<String,Handler> handlers=new LinkedHashMap<>();Thread worker;volatile Throwable failure;
        private HttpServer(InetSocketAddress address)throws IOException{socket=new ServerSocket();socket.bind(address);}
        static HttpServer create(InetSocketAddress address,int backlog)throws IOException{return new HttpServer(address);}
        InetSocketAddress getAddress(){return (InetSocketAddress)socket.getLocalSocketAddress();}
        void createContext(String path,Handler handler){handlers.put(path,handler);}
        void start(){worker=new Thread(()->{while(!socket.isClosed())try(Socket client=socket.accept()){
            client.setSoTimeout(3000);Exchange exchange=new Exchange(client);Handler handler=null;
            for(Map.Entry<String,Handler> entry:handlers.entrySet())if(exchange.path.startsWith(entry.getKey()))handler=entry.getValue();
            if(handler==null)throw new IOException("Unknown path");handler.handle(exchange);
        }catch(Throwable error){if(!socket.isClosed())failure=error;}},"http-client-test");worker.setDaemon(true);worker.start();}
        void stop(int delay)throws Exception{socket.close();worker.join(4000);if(failure!=null)throw new AssertionError(failure);}
    }
    private static final String TOKEN="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ID="20000000-0000-4000-8000-000000000001";
    private static final String PHOTO="/api/v1/plugins/photoframe/photos/"+ID+"/"+TOKEN;
    private static final String MUSIC="/api/v1/music/stream/"+ID+"/1/"+ID;

    @Test public void pairingApiPhotosAndMusicUseHttpWithCredentials()throws Exception {
        AtomicInteger failures=new AtomicInteger();AtomicInteger requests=new AtomicInteger();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/",exchange->{
            requests.incrementAndGet();String path=exchange.getRequestURI().getPath();
            boolean pair=path.equals("/api/v1/pair");
            if(pair ? exchange.getRequestHeaders().getFirst("Authorization")!=null
                : !("Bearer "+TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    || !ID.equals(exchange.getRequestHeaders().getFirst("X-MediaCentre-Server-Id"))) failures.incrementAndGet();
            String type="application/json";String body="{\"data\":{}}";int status=200;
            if(pair){
                if(!exchange.getRequestMethod().equals("POST"))failures.incrementAndGet();
                ByteArrayOutputStream posted=new ByteArrayOutputStream();byte[] buffer=new byte[256];int n;
                InputStream input=exchange.getRequestBody();while((n=input.read(buffer))!=-1)posted.write(buffer,0,n);
                if(!posted.toString("UTF-8").contains("ABCDEFGH"))failures.incrementAndGet();
                body="{\"data\":{\"token\":\""+TOKEN+"\",\"server_id\":\""+ID+"\",\"client_id\":\""+ID+"\",\"scopes\":[]}}";
            } else if(path.equals(PHOTO)){type="image/jpeg";body="photo-bytes";}
            else if(path.equals(MUSIC)){
                type="audio/mpeg";body="ABC";status=206;
                if(!"bytes=2-4".equals(exchange.getRequestHeaders().getFirst("Range")))failures.incrementAndGet();
                exchange.getResponseHeaders().set("Content-Range","bytes 2-4/10");
            }
            byte[] bytes=body.getBytes("UTF-8");exchange.getResponseHeaders().set("Content-Type",type);
            exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try{
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            assertEquals(TOKEN,new ApiClient(null,base,null).pair("ABCDEFGH","HTTP test").token);
            ApiClient api=new ApiClient(null,base,TOKEN,ID);
            assertEquals(200,api.fetch("/api/v1/bootstrap",null).code);
            assertEquals("photo-bytes",new String(api.photo(PHOTO),"UTF-8"));
            try(MusicStreamBridge bridge=new MusicStreamBridge(api,MUSIC)){
                HttpURLConnection audio=(HttpURLConnection)new URL(bridge.url()).openConnection();
                audio.setConnectTimeout(3000);audio.setReadTimeout(3000);audio.setRequestProperty("Range","bytes=2-4");
                try{
                    assertEquals(206,audio.getResponseCode());assertEquals("bytes 2-4/10",audio.getHeaderField("Content-Range"));
                    assertEquals('A',audio.getInputStream().read());
                }finally{audio.disconnect();}
            }
            assertEquals(4,requests.get());assertEquals(0,failures.get());
        }finally{server.stop(0);}
    }

    @Test public void redirectsNeverForwardCredentialsOrReturnMedia()throws Exception {
        AtomicInteger leaked=new AtomicInteger();HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/",exchange->{exchange.getResponseHeaders().set("Location","/target");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.createContext("/target",exchange->{leaked.incrementAndGet();exchange.sendResponseHeaders(200,-1);exchange.close();});server.start();
        try{
            ApiClient api=new ApiClient(null,"http://127.0.0.1:"+server.getAddress().getPort(),TOKEN,ID);
            assertEquals(302,api.fetch("/api/v1/bootstrap",null).code);
            try{api.photo(PHOTO);fail("Redirected photo accepted");}catch(ApiClient.HttpError expected){assertEquals(302,expected.code);}
            HttpURLConnection audio=api.openMusic(MUSIC,null);try{assertEquals(302,audio.getResponseCode());}finally{audio.disconnect();}
            assertEquals(0,leaked.get());
        }finally{server.stop(0);}
    }

    @Test public void invalidOriginsRemainRejected()throws Exception {
        for(String base:new String[]{"ftp://server","http://user:pass@server","http://server/path","http://server?x=y","http://server#x","http://server:0","http://server:70000","http://bad host"}){
            try{new ApiClient(null,base,null);fail("Invalid origin accepted: "+base);}catch(IOException expected){}
        }
        new ApiClient(null,"http://mediacentre.miniw",null);
    }
}

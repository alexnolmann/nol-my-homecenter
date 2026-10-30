package local.mediacentre.client.music;
import local.mediacentre.client.data.ApiClient;
import java.io.*;
import java.net.*;

import java.util.*;
import java.util.concurrent.*;
/** API19 native player consumes loopback HTTP; this bridge uses the configured HTTP/HTTPS upstream.
 * Bounded buffers, two connections, random capability path, no files and no public listener. */
public final class MusicStreamBridge implements Closeable {
    private final ServerSocket listener=new ServerSocket();
    public interface Source {HttpURLConnection open(String range)throws IOException;}
    private final String capability=UUID.randomUUID().toString();
    private final Source source;
    private static final ThreadPoolExecutor WORKERS=pool(2,4,"music-stream");
    private static final ThreadPoolExecutor CANCEL=pool(1,4,"music-cancel");
    private final Map<Socket,Runnable> jobs=new HashMap<>();
    private static ThreadPoolExecutor pool(int threads,int queue,String name){ThreadPoolExecutor p=new ThreadPoolExecutor(threads,threads,15L,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(queue),r->{Thread t=new Thread(r,name);t.setDaemon(true);return t;});p.allowCoreThreadTimeOut(true);return p;}
    private final Set<Socket> sockets=new HashSet<>();
    private final Set<HttpURLConnection> upstreams=new HashSet<>();
    private volatile boolean closed;
    public MusicStreamBridge(ApiClient api,String path)throws IOException {
        this(range->api.openMusic(path,range));
    }
    public MusicStreamBridge(Source source)throws IOException {
        this.source=source;listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),2);
        Thread accept=new Thread(()->{while(!closed)try{Socket socket=listener.accept();socket.setSoTimeout(10000);synchronized(this){if(closed){socket.close();break;}sockets.add(socket);}
            Runnable job=()->serve(socket);synchronized(this){if(closed){sockets.remove(socket);socket.close();break;}jobs.put(socket,job);}
            try{WORKERS.execute(job);}catch(RejectedExecutionException busy){synchronized(this){sockets.remove(socket);jobs.remove(socket);}socket.close();}
        }catch(IOException stopped){break;}},"music-loopback");accept.setDaemon(true);accept.start();
    }
    public String url(){return "http://127.0.0.1:"+listener.getLocalPort()+"/"+capability+".mp3";}
    private static String line(InputStream input,int maximum)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();int n;while((n=input.read())!=-1){if(n=='\n')break;if(out.size()>=maximum)throw new IOException("HTTP header too large.");if(n!='\r')out.write(n);}return out.toString("US-ASCII");
    }
    private void serve(Socket socket){
        HttpURLConnection remote=null;
        try{
            InputStream request=socket.getInputStream();String first=line(request,2048);String[] parts=first.split(" ");
            if(parts.length!=3||!parts[1].equals("/"+capability+".mp3")||!(parts[0].equals("GET")||parts[0].equals("HEAD"))){status(socket,404);return;}
            String range=null;int total=first.length();
            for(int n=0;n<40;n++){String header=line(request,2048);total+=header.length();if(total>8192)throw new IOException("HTTP headers too large.");if(header.isEmpty())break;if(n==39)throw new IOException("Too many headers.");int colon=header.indexOf(':');if(colon>0&&header.substring(0,colon).equalsIgnoreCase("Range"))range=header.substring(colon+1).trim();}
            if(range!=null&&!range.matches("bytes=[0-9]{1,18}-[0-9]{0,18}")){status(socket,416);return;}
            remote=source.open(range);
            synchronized(this){if(closed){remote.disconnect();return;}upstreams.add(remote);}
            int code=remote.getResponseCode();if(code!=200&&code!=206){status(socket,code);return;}
            if(!"audio/mpeg".equals(remote.getContentType()))throw new IOException("Unexpected audio response.");
            String rawLength=remote.getHeaderField("Content-Length");if(rawLength==null||!rawLength.matches("[0-9]{1,18}"))throw new IOException("No audio length.");
            long remaining=Long.parseLong(rawLength);String contentRange=remote.getHeaderField("Content-Range");
            if(code==206&&(contentRange==null||!contentRange.matches("bytes [0-9]+-[0-9]+/[0-9]+")))throw new IOException("Invalid audio range.");
            OutputStream out=socket.getOutputStream();String head="HTTP/1.1 "+code+(code==206?" Partial Content":" OK")+"\r\nContent-Type: audio/mpeg\r\nContent-Length: "+rawLength+"\r\nAccept-Ranges: bytes\r\nCache-Control: no-store\r\nConnection: close\r\n"+(code==206?"Content-Range: "+contentRange+"\r\n":"")+"\r\n";out.write(head.getBytes("US-ASCII"));out.flush();
            if(parts[0].equals("HEAD"))return;
            try(InputStream input=remote.getInputStream()){byte[] buffer=new byte[32768];while(!closed&&remaining>0){int count=input.read(buffer,0,(int)Math.min(buffer.length,remaining));if(count<0)break;out.write(buffer,0,count);remaining-=count;}}
        }catch(Exception unavailable){try{status(socket,502);}catch(IOException ignored){}}
        finally{if(remote!=null){remote.disconnect();synchronized(this){upstreams.remove(remote);}}synchronized(this){sockets.remove(socket);jobs.remove(socket);}try{socket.close();}catch(IOException ignored){}}
    }
    private static void status(Socket socket,int code)throws IOException {socket.getOutputStream().write(("HTTP/1.1 "+code+" Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));}
    @Override public void close(){
        List<Socket> clients;List<HttpURLConnection> connections;List<Runnable> pending;
        synchronized(this){if(closed)return;closed=true;clients=new ArrayList<>(sockets);connections=new ArrayList<>(upstreams);pending=new ArrayList<>(jobs.values());jobs.clear();}
        try{listener.close();}catch(IOException ignored){}for(Socket s:clients)try{s.close();}catch(IOException ignored){}
        for(Runnable job:pending)WORKERS.remove(job);
        // Disconnect may wait for a network read lock; never block Android's UI/audio callback thread.
        if(!connections.isEmpty())try{CANCEL.execute(()->{for(HttpURLConnection c:connections)c.disconnect();});}catch(RejectedExecutionException busy){/* Bounded workers and read timeouts still guarantee cleanup. */}
    }
}

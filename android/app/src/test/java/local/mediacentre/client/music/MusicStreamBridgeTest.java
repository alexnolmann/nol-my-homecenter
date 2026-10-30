package local.mediacentre.client.music;
import org.junit.Test;
import static org.junit.Assert.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.security.cert.Certificate;
import java.util.concurrent.atomic.AtomicInteger;
public class MusicStreamBridgeTest {
 private static class Fake extends HttpsURLConnection {
  final int status;final String type;final byte[] data;
  Fake(int status,String type,byte[] data)throws Exception{super(new URL("https://invalid.example"));this.status=status;this.type=type;this.data=data;}
  public int getResponseCode(){return status;}public String getContentType(){return type;}
  public String getHeaderField(String name){if(name.equalsIgnoreCase("Content-Length"))return ""+data.length;if(name.equalsIgnoreCase("Content-Range"))return "bytes 2-4/10";return null;}
  public InputStream getInputStream(){return new ByteArrayInputStream(data);}
  public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}
  public String getCipherSuite(){return "test";}public Certificate[] getLocalCertificates(){return null;}public Certificate[] getServerCertificates(){return null;}
 }
 private static String request(String url,String path,String range)throws Exception{
  URL u=new URL(url);try(Socket s=new Socket("127.0.0.1",u.getPort())){
   s.setSoTimeout(5000);s.getOutputStream().write(("GET "+(path==null?u.getPath():path)+" HTTP/1.1\r\nHost: localhost\r\n"+(range==null?"":"Range: "+range+"\r\n")+"\r\n").getBytes("US-ASCII"));
   ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[256];int n;while((n=s.getInputStream().read(b))!=-1)out.write(b,0,n);return out.toString("US-ASCII");
  }
 }
 @Test public void rangeAndCapability()throws Exception{
  AtomicInteger calls=new AtomicInteger();Fake response=new Fake(206,"audio/mpeg",new byte[]{65,66,67});
  try(MusicStreamBridge b=new MusicStreamBridge(range->{assertEquals("bytes=2-4",range);calls.incrementAndGet();return response;})){
   String url=b.url();assertTrue(url.startsWith("http://127.0.0.1:"));String result=request(url,null,"bytes=2-4");assertTrue(result.startsWith("HTTP/1.1 206"));assertTrue(result.contains("Content-Range: bytes 2-4/10"));assertTrue(result.endsWith("ABC"));
   assertTrue(request(url,"/wrong.mp3",null).startsWith("HTTP/1.1 404"));assertTrue(request(url,null,"bytes=0-1,4-5").startsWith("HTTP/1.1 416"));assertEquals(1,calls.get());
  }
 }
 @Test public void rejectsUnexpectedContentAndAuth()throws Exception{
  Fake html=new Fake(200,"text/html",new byte[0]);try(MusicStreamBridge b=new MusicStreamBridge(range->html)){assertTrue(request(b.url(),null,null).startsWith("HTTP/1.1 502"));}
  Fake revoked=new Fake(401,"application/json",new byte[0]);try(MusicStreamBridge b=new MusicStreamBridge(range->revoked)){assertTrue(request(b.url(),null,null).startsWith("HTTP/1.1 401"));}
 }
}

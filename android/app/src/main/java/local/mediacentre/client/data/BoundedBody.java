package local.mediacentre.client.data;
import java.io.*;
/** Independent of Content-Length: the bound applies to bytes actually consumed. */
public final class BoundedBody {
    private BoundedBody(){}
    public static String read(InputStream input)throws IOException{
        ByteArrayOutputStream body=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int length;
        while((length=input.read(buffer))!=-1){if(body.size()+length>JsonCache.MAX_BODY)throw new IOException("Ответ превышает 256 KiB.");body.write(buffer,0,length);}
        return body.toString("UTF-8");
    }
}

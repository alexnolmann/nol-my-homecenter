package local.mediacentre.client;
import java.io.*;
import java.util.Arrays;
import local.mediacentre.client.data.*;
import org.junit.Test;
import static org.junit.Assert.*;
public final class BoundedBodyTest {
    @Test public void exactLimitAndUtf8AreMeasuredInBytes()throws Exception{
        char[] chars=new char[JsonCache.MAX_BODY/2];Arrays.fill(chars,'я');String text=new String(chars);
        assertEquals(text,BoundedBody.read(new ByteArrayInputStream(text.getBytes("UTF-8"))));
        try{BoundedBody.read(new ByteArrayInputStream((text+"я").getBytes("UTF-8")));fail("Overflow accepted");}catch(IOException expected){}
    }
    @Test public void endlessChunkedInputStopsBeforeReadingMoreThanOneExtraBlock()throws Exception{
        final long[] count={0};InputStream input=new InputStream(){public int read(){count[0]++;return 'x';}};
        try{BoundedBody.read(input);fail("Endless input accepted");}catch(IOException expected){}
        assertTrue(count[0]<=JsonCache.MAX_BODY+4096);
    }
}

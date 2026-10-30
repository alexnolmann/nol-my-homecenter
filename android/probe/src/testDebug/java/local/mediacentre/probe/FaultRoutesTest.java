package local.mediacentre.probe;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public final class FaultRoutesTest {
    @Test public void routesCoverPublicSpaceExactlyOnceWithoutAnyPrivateRange(){
        List<FaultRoutes.Route> routes=FaultRoutes.internet();long previousEnd=0,total=0;
        for(FaultRoutes.Route route:routes){assertTrue(route.address>=previousEnd);long size=1L<<(32-route.prefix);assertEquals(0,route.address%size);previousEnd=route.address+size;total+=size;}
        assertEquals((1L<<32)-2*(1L<<24)-(1L<<20)-2*(1L<<16),total);
        for(long address:new long[]{0x0a000000L,0x0affffffL,0x7f000001L,0xa9feffffL,0xac100000L,0xac1fffffL,0xc0a80250L,0xc0a8ffffL})assertFalse(covered(routes,address));
        for(long address:new long[]{0x08080808L,0x01010101L,0x0b000000L,0xac0fffffL,0xac200000L,0xc0a7ffffL,0xc0a90000L})assertTrue(covered(routes,address));
    }
    private boolean covered(List<FaultRoutes.Route> routes,long address){for(FaultRoutes.Route route:routes)if(route.contains(address))return true;return false;}
    @Test public void serverFaultOnlyAcceptsLiteralPrivateIpv4(){
        for(String host:new String[]{"10.1.2.3","172.16.0.1","172.31.255.254","192.168.50.10"})assertTrue(FaultRoutes.privateHost(host));
        for(String host:new String[]{"8.8.8.8","example.org","192.168.999.1","127.0.0.1","172.32.0.1","1.2.3","::1"})assertFalse(FaultRoutes.privateHost(host));assertFalse(FaultRoutes.privateHost(null));
    }
}

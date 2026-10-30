package local.mediacentre.probe;
import java.util.*;

/** Public IPv4 routes, preserving loopback, RFC1918 and link-local LAN access. */
public final class FaultRoutes {
    private static final long[][] EXCLUDED={{0x0a000000L,8},{0x7f000000L,8},{0xa9fe0000L,16},{0xac100000L,12},{0xc0a80000L,16}};
    public static final class Route {
        public final long address;public final int prefix;
        Route(long address,int prefix){this.address=address;this.prefix=prefix;}
        public String ip(){return ((address>>>24)&255)+"."+((address>>>16)&255)+"."+((address>>>8)&255)+"."+(address&255);}
        public boolean contains(long ip){return ip>=address&&ip<address+(1L<<(32-prefix));}
    }
    public static List<Route> internet(){List<Route> result=new ArrayList<>();split(0,0,result);return result;}
    private static void split(long start,int prefix,List<Route> result){
        long end=start+(1L<<(32-prefix));boolean overlap=false;
        for(long[] excluded:EXCLUDED){long last=excluded[0]+(1L<<(32-excluded[1]));if(start>=excluded[0]&&end<=last)return;if(start<last&&end>excluded[0])overlap=true;}
        if(!overlap){result.add(new Route(start,prefix));return;}
        split(start,prefix+1,result);split(start+(1L<<(31-prefix)),prefix+1,result);
    }
    public static boolean privateHost(String host){
        if(host==null||!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))return false;
        long address=0;for(String part:host.split("\\.")){int n=Integer.parseInt(part);if(n>255)return false;address=(address<<8)|n;}
        return (address>>>24)==10||(address>>>20)==0xac1||(address>>>16)==0xc0a8;
    }
}

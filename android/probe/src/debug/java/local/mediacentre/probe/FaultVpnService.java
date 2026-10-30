package local.mediacentre.probe;
import android.app.*;
import android.content.Intent;
import android.net.VpnService;
import android.os.*;
import android.util.Log;

/** Drop-only TUN. No socket, remote endpoint, packet reader, capture or disk output. */
public final class FaultVpnService extends VpnService {
    private final Handler handler=new Handler();private ParcelFileDescriptor tunnel;
    private final Runnable deadline=()->stopSelf();
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null||Build.VERSION.SDK_INT!=19){stopSelf();return START_NOT_STICKY;}
        close();String mode=intent.getStringExtra("mode"),host=intent.getStringExtra("host");
        try{
            Builder builder=new Builder().setSession("H6: временная потеря сети (180 с)").setMtu(1500).addAddress("198.18.0.1",32);
            if("internet".equals(mode)){
                for(FaultRoutes.Route route:FaultRoutes.internet())builder.addRoute(route.ip(),route.prefix);
                builder.addAddress("fd00:6::1",128).addRoute("2000::",3);
            }else if("server".equals(mode)&&FaultRoutes.privateHost(host)){
                builder.addRoute(host,32);
                // Enable IPv6 without routing it through this IPv4-only failure.
                builder.addAddress("fd00:6::1",128);
            }else throw new IllegalArgumentException("Unknown fault");
            tunnel=builder.establish();if(tunnel==null)throw new IllegalStateException("VPN consent absent");
            PendingIntent stop=PendingIntent.getActivity(this,6,new Intent(this,FaultActivity.class).putExtra("mode","stop"),PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notice=new Notification.Builder(this).setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle("H6: временная потеря сети").setContentText("Автоотключение через 180 с · нажмите для восстановления").setContentIntent(stop).setOngoing(true).build();
            startForeground(606,notice);handler.postDelayed(deadline,180000);
            Log.i("MediaCentreH6","H6_VPN_STARTED mode="+mode+" ttl_seconds=180");
        }catch(Exception error){Log.i("MediaCentreH6","H6_VPN_ERROR type="+error.getClass().getSimpleName());close();stopSelf();}
        return START_NOT_STICKY;
    }
    private void close(){handler.removeCallbacks(deadline);if(tunnel!=null){try{tunnel.close();}catch(java.io.IOException ignored){}tunnel=null;}stopForeground(true);}
    @Override public void onRevoke(){handler.post(()->{close();stopSelf();});}
    @Override public void onDestroy(){close();Log.i("MediaCentreH6","H6_VPN_STOPPED");super.onDestroy();}
}

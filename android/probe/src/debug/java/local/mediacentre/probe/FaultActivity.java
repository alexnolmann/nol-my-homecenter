package local.mediacentre.probe;
import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.*;
import android.util.Log;
import android.widget.*;

/** Explicit, user-approved, time-bounded network fault injection on the API19 test tablet. */
public final class FaultActivity extends Activity {
    private String mode,host;private TextView status;
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);recreate();}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);mode=getIntent().getStringExtra("mode");host=getIntent().getStringExtra("host");
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(32,32,32,32);
        status=new TextView(this);status.setTextSize(22);box.addView(status);
        Button stop=new Button(this);stop.setText("Восстановить сеть сейчас");stop.setOnClickListener(v->{if("wifi_off".equals(mode)||"wifi_on".equals(mode)){WifiFaultReceiver.toggle(this,true);status.setText("Wi-Fi включается");}else{stopService(new Intent(this,FaultVpnService.class));status.setText("Тестовый VPN остановлен");}});box.addView(stop);setContentView(box);
        if("stop".equals(mode)){stopService(new Intent(this,FaultVpnService.class));status.setText("Тестовый VPN остановлен");return;}
        if(Build.VERSION.SDK_INT==19&&("wifi_off".equals(mode)||"wifi_on".equals(mode))){WifiFaultReceiver.toggle(this,"wifi_on".equals(mode));status.setText("H6: Wi-Fi "+("wifi_on".equals(mode)?"включается":"выключается; восстановление через 180 с"));return;}
        if(Build.VERSION.SDK_INT!=19||!("internet".equals(mode)||"server".equals(mode)&&FaultRoutes.privateHost(host))){status.setText("Нужен тестовый ASUS API19 и корректный режим internet/server");return;}
        status.setText("H6: временная потеря "+("internet".equals(mode)?"интернета, локальная IPv4-сеть доступна":"доступа к одному локальному серверу")+".\nАвтоотключение через 180 секунд.\nДанные никуда не передаются и не записываются.\nVPN влияет на все приложения планшета и заменяет другой активный VPN.");
        Intent consent=VpnService.prepare(this);
        if(consent!=null){Log.i("MediaCentreH6","H6_VPN_CONSENT_REQUIRED");startActivityForResult(consent,6);}else begin();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==6&&result==RESULT_OK)begin();else Log.i("MediaCentreH6","H6_VPN_CONSENT_DECLINED");}
    private void begin(){startService(new Intent(this,FaultVpnService.class).putExtra("mode",mode).putExtra("host",host));}
}

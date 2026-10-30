package local.mediacentre.client.data;
import android.content.*;
import android.os.SystemClock;
import java.io.*;
import java.util.Arrays;
import org.json.*;
/** Real ASUS-to-server HTTPS measurements; neither responses nor auth leave app-private memory. */
public final class ApiBenchmark {
    public static void run(Context context)throws Exception{
        SharedPreferences p=context.getSharedPreferences("connection",Context.MODE_PRIVATE);
        ApiClient client=new ApiClient(context,p.getString("base_url",""),p.getString("token",""),p.getString("server_id",null));
        JSONArray results=new JSONArray();
        for(String path:new String[]{"bootstrap","dashboard","weather","finance"}){
            long[] times=new long[30];JSONArray samples=new JSONArray();
            for(int n=-2;n<times.length;n++){
                long begin=SystemClock.elapsedRealtime();ApiClient.Response response=client.fetch("/api/v1/"+path+"?scenario=database",null);long duration=SystemClock.elapsedRealtime()-begin;
                if(response.code!=200||response.body==null)throw new IOException("Benchmark request failed");
                if(n>=0){times[n]=duration;samples.put(duration);}
            }
            Arrays.sort(times);results.put(new JSONObject().put("path",path).put("samples_ms",samples).put("p95_ms",times[28]).put("max_ms",times[29]).put("warmup",2));
        }
        File folder=context.getExternalFilesDir(null);if(folder==null)throw new IOException("No diagnostics folder");
        try(OutputStream out=new FileOutputStream(new File(folder,"h6-api-benchmark.json"))){out.write(results.toString().getBytes("UTF-8"));}
    }
}

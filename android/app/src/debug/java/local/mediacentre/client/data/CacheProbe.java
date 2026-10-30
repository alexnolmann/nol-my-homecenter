package local.mediacentre.client.data;
import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.Iterator;
/** Debug APK only: hashes/chronology; value samples are restricted to synthetic responses. */
public final class CacheProbe {
    public static void recovery(Context context)throws Exception {
        AtomicFile probe=new AtomicFile(new File(context.getFilesDir(),"h6-atomic-recovery-probe.json"));
        if(probe.getBaseFile().exists()||new File(probe.getBaseFile()+".bak").exists())throw new IOException("Prior recovery probe must be inspected first");
        byte[] original="{\"price\":\"12.123456789\",\"fetched_at\":\"2026-09-27T08:00:00Z\"}".getBytes("UTF-8");
        try{
            FileOutputStream complete=probe.startWrite();complete.write(original);probe.finishWrite(complete);
            // Close a deliberately interrupted replacement without finishWrite/failWrite.
            FileOutputStream broken=probe.startWrite();broken.write("{\"partial\":".getBytes("UTF-8"));broken.close();
            AtomicFile reopened=new AtomicFile(probe.getBaseFile());ByteArrayOutputStream actual=new ByteArrayOutputStream();
            try(InputStream input=reopened.openRead()){byte[] block=new byte[256];int n;while((n=input.read(block))!=-1)actual.write(block,0,n);}
            if(!java.util.Arrays.equals(original,actual.toByteArray()))throw new IOException("Atomic recovery differs");
        }finally{probe.delete();}
    }
    public static void export(Context context)throws Exception {
        synchronized(PanelRepository.LOCK){
            AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"panel-cache-v1.json"));
            android.content.SharedPreferences connection=context.getSharedPreferences("connection",Context.MODE_PRIVATE);
            JSONObject summary=new JSONObject().put("cache_present",false).put("connection_has_token",connection.contains("token")).put("connection_has_server_id",connection.contains("server_id"));
            if(file.getBaseFile().exists() || new File(file.getBaseFile().getPath()+".bak").exists()){
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream input=file.openRead()){byte[] block=new byte[4096];int count;while((count=input.read(block))!=-1){if(out.size()+count>JsonCache.MAX_TOTAL)throw new IOException("Oversized cache");out.write(block,0,count);}}
                JSONObject root=new JSONObject(out.toString("UTF-8"));JSONObject entries=root.getJSONObject("entries"),records=new JSONObject();
                Iterator<String> keys=entries.keys();
                while(keys.hasNext()){
                    String path=keys.next(),body=entries.getJSONObject(path).getString("body");JSONObject response=new JSONObject(body);
                    boolean synthetic=response.getJSONObject("meta").getBoolean("test_data");
                    byte[] hash=MessageDigest.getInstance("SHA-256").digest(body.getBytes("UTF-8"));StringBuilder hex=new StringBuilder();for(byte b:hash)hex.append(String.format(java.util.Locale.US,"%02x",b&255));
                    JSONObject record=new JSONObject().put("body_sha256",hex.toString()).put("test_data",synthetic);
                    for(String date:new String[]{"fetched_at","valid_until","data_as_of"})record.put(date,response.getJSONObject("meta").opt(date));
                    if(synthetic&&path.startsWith("/api/v1/finance"))record.put("sample",response.getJSONObject("data"));
                    if(synthetic&&path.startsWith("/api/v1/radio/stations/")){JSONObject data=response.getJSONObject("data");record.put("stream_url",data.getString("stream_url"));record.put("favorite",data.getBoolean("favorite"));}
                    records.put(path,record);
                }
                summary.put("cache_present",true).put("bytes",out.size()).put("count",entries.length()).put("entries",records);
            }
            File directory=context.getExternalFilesDir(null);if(directory==null)throw new IOException("Debug diagnostics directory unavailable");
            try(OutputStream output=new FileOutputStream(new File(directory,"h2-cache-summary.json"))){output.write(summary.toString().getBytes("UTF-8"));}
        }
    }
}

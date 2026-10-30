package local.mediacentre.client.data;
import android.content.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;

/** Private, debug-only fixture safeguard. Never exports registration to host/shared storage. */
public final class ProfileProbe {
    private static final String[] NAMES={"connection","radio_station","display"};
    private static byte[] read(AtomicFile file,int limit)throws IOException {
        try(InputStream in=file.openRead()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[4096];int count;while((count=in.read(bytes))!=-1){if(out.size()+count>limit)throw new IOException("Oversized private file");out.write(bytes,0,count);}return out.toByteArray();}
    }
    private static void write(AtomicFile file,byte[] bytes)throws IOException {
        FileOutputStream out=null;try{out=file.startWrite();out.write(bytes);file.finishWrite(out);}catch(IOException e){if(out!=null)file.failWrite(out);throw e;}
    }
    private static boolean exists(AtomicFile file){return file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists();}
    public static void run(Context context,String action)throws Exception {
        synchronized(PanelRepository.LOCK){
            AtomicFile backup=new AtomicFile(new File(context.getFilesDir(),"h5-private-profile.json"));
            AtomicFile cache=new AtomicFile(new File(context.getFilesDir(),"panel-cache-v1.json"));
            if("backup".equals(action)){
                if(exists(backup))throw new IOException("Restore existing backup first");
                JSONObject root=new JSONObject(),preferences=new JSONObject();
                for(String name:NAMES){JSONObject values=new JSONObject();for(Map.Entry<String,?> entry:context.getSharedPreferences(name,Context.MODE_PRIVATE).getAll().entrySet()){
                    Object value=entry.getValue();String type=value instanceof String?"string":value instanceof Boolean?"boolean":value instanceof Integer?"int":value instanceof Long?"long":value instanceof Float?"float":null;
                    if(type==null)throw new IOException("Unsupported preference");values.put(entry.getKey(),new JSONObject().put("type",type).put("value",value));
                }preferences.put(name,values);}
                root.put("preferences",preferences).put("cache",exists(cache)?new String(read(cache,JsonCache.MAX_TOTAL),"UTF-8"):JSONObject.NULL);
                write(backup,root.toString().getBytes("UTF-8"));return;
            }
            if(!"restore".equals(action)||!exists(backup))throw new IOException("Private backup required");
            JSONObject root=new JSONObject(new String(read(backup,JsonCache.MAX_TOTAL*2),"UTF-8")),preferences=root.getJSONObject("preferences");
            // Build every editor before mutating any preferences; recovery is repeatable if interrupted.
            List<SharedPreferences.Editor> editors=new ArrayList<>();
            for(String name:NAMES){SharedPreferences.Editor editor=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear();JSONObject values=preferences.getJSONObject(name);Iterator<String> keys=values.keys();while(keys.hasNext()){
                String key=keys.next();JSONObject value=values.getJSONObject(key);switch(value.getString("type")){
                    case "string":editor.putString(key,value.getString("value"));break;
                    case "boolean":editor.putBoolean(key,value.getBoolean("value"));break;
                    case "int":editor.putInt(key,value.getInt("value"));break;
                    case "long":editor.putLong(key,value.getLong("value"));break;
                    case "float":editor.putFloat(key,(float)value.getDouble("value"));break;
                    default:throw new IOException("Invalid private preference type");
                }
            }editors.add(editor);}
            for(SharedPreferences.Editor editor:editors)if(!editor.commit())throw new IOException("Private restoration failed");
            cache.delete();if(!root.isNull("cache"))write(cache,root.getString("cache").getBytes("UTF-8"));
            for(String name:NAMES){Map<String,?> actual=context.getSharedPreferences(name,Context.MODE_PRIVATE).getAll();JSONObject expected=preferences.getJSONObject(name);if(actual.size()!=expected.length())throw new IOException("Restored preference count differs");Iterator<String> keys=expected.keys();while(keys.hasNext()){
                String key=keys.next();JSONObject record=expected.getJSONObject(key);Object value=actual.get(key),wanted;switch(record.getString("type")){
                    case "string":wanted=record.getString("value");break;case "boolean":wanted=record.getBoolean("value");break;case "int":wanted=record.getInt("value");break;case "long":wanted=record.getLong("value");break;case "float":wanted=(float)record.getDouble("value");break;default:throw new IOException("Invalid private type");
                }if(!wanted.equals(value))throw new IOException("Restored preference differs");
            }}
            if(root.isNull("cache")?exists(cache):!Arrays.equals(root.getString("cache").getBytes("UTF-8"),read(cache,JsonCache.MAX_TOTAL)))throw new IOException("Restored cache differs");
            backup.delete();if(exists(backup))throw new IOException("Backup removal failed");
        }
    }
}

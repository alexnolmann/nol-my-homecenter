package local.mediacentre.client.ui;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.json.*;
import local.mediacentre.client.R;

/** Read-only native view identities; no preferences, bearer tokens, URLs or audio samples. */
public final class StationsLayoutProbe {
    public static void snapshot(Activity a)throws Exception{
        View root=a.findViewById(R.id.content);ListView list=(ListView)find(root,"station_list");EditText search=(EditText)find(root,"station_search");
        if(list==null||search==null)throw new IllegalStateException("Open Neo Poster stations first");
        JSONArray rows=new JSONArray();for(int n=0;n<list.getChildCount();n++){View row=list.getChildAt(n);String key=String.valueOf(row.getContentDescription());String id=key.substring("station_row_".length());View favorite=find(row,"favorite_station_"+id);rows.put(new JSONObject().put("id",id).put("identity",System.identityHashCode(row)).put("favorite",favorite instanceof TextView&&"★".contentEquals(((TextView)favorite).getText())).put("favorite_enabled",favorite!=null&&favorite.isEnabled()));}
        JSONObject report=new JSONObject().put("list_identity",System.identityHashCode(list)).put("search_identity",System.identityHashCode(search)).put("count",list.getAdapter().getCount()).put("first",list.getFirstVisiblePosition()).put("top",list.getChildCount()==0?0:list.getChildAt(0).getTop()).put("query_length",search.length()).put("search_focused",search.hasFocus()).put("selection",search.getSelectionStart()).put("rows",rows);
        File directory=a.getExternalFilesDir(null);if(directory==null)throw new IOException("No diagnostic directory");try(OutputStream out=new FileOutputStream(new File(directory,"stations-layout-snapshot.json"))){out.write(report.toString().getBytes("UTF-8"));}
    }
    private static View find(View v,String key){if(key.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){View hit=find(g.getChildAt(n),key);if(hit!=null)return hit;}}return null;}
}

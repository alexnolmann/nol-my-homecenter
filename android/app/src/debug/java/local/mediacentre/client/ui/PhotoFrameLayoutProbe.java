package local.mediacentre.client.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
import local.mediacentre.client.R;

/** Native diagnostics and an empty-album fixture. No photo IDs, URLs, pixels or server writes. */
public final class PhotoFrameLayoutProbe {
    private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    public static void run(Activity a,String mode)throws Exception{
        Object screen=field(a,"photoFrameScreen"),preview=field(screen,"preview");if(preview==null)throw new IllegalStateException("Open photo frame first");
        if("empty".equals(mode)){
            Method apply=preview.getClass().getDeclaredMethod("apply",ArrayList.class);apply.setAccessible(true);apply.invoke(preview,new ArrayList<>());
            ((TextView)field(screen,"summary")).setText("ТЕСТ · фотографий пока нет");((Button)field(screen,"start")).setEnabled(false);View title=find(a.findViewById(R.id.content),"photoframe_title");if(title instanceof TextView)((TextView)title).setText("ТЕСТ");return;
        }
        if(!"snapshot".equals(mode))throw new IllegalArgumentException("Unknown diagnostic");
        SharedPreferences p=a.getSharedPreferences("photoframe",0);List<?> photos=(List<?>)field(preview,"thumbnails");LinearLayout strip=(LinearLayout)field(preview,"strip");ImageView image=(ImageView)field(preview,"image");
        JSONObject data=new JSONObject().put("seconds",p.getInt("seconds",10)).put("shuffle",p.getBoolean("shuffle",false)).put("fill",p.getBoolean("fill",false)).put("preview_count",photos.size()).put("thumb_count",strip==null?0:strip.getChildCount()).put("current",field(preview,"current")).put("scale_type",image.getScaleType().name());
        JSONArray sizes=new JSONArray();for(Object photo:photos){Bitmap bitmap=(Bitmap)field(photo,"bitmap");sizes.put(new JSONArray(new int[]{bitmap.getWidth(),bitmap.getHeight()}));}data.put("bitmap_sizes",sizes);
        File dir=a.getExternalFilesDir(null);if(dir==null)throw new IOException("No diagnostic directory");try(OutputStream out=new FileOutputStream(new File(dir,"photoframe-layout-snapshot.json"))){out.write(data.toString().getBytes("UTF-8"));}
    }
    private static View find(View view,String description){if(description.equals(view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int n=0;n<g.getChildCount();n++){View hit=find(g.getChildAt(n),description);if(hit!=null)return hit;}}return null;}
}

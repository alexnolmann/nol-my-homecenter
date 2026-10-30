package local.mediacentre.client.ui;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.json.*;
import local.mediacentre.client.R;

/** Physical header invariants only: no visible text, device names, URLs or connection settings. */
public final class HeaderLayoutProbe {
    public static void snapshot(Activity a)throws Exception{
        TextView clock=a.findViewById(R.id.header_clock);ViewGroup output=a.findViewById(R.id.audio_output);
        JSONObject result=new JSONObject().put("clock",metrics(clock)).put("output",metrics(output));JSONArray buttons=new JSONArray();
        for(int n=0;n<output.getChildCount();n++)buttons.put(metrics(output.getChildAt(n)));result.put("buttons",buttons);
        File dir=a.getExternalFilesDir(null);if(dir==null)throw new IOException("Diagnostic directory unavailable");
        try(OutputStream out=new FileOutputStream(new File(dir,"header-layout-snapshot.json"))){out.write(result.toString().getBytes("UTF-8"));}
    }
    private static JSONObject metrics(View view)throws Exception{
        Rect rect=new Rect();boolean shown=view.getGlobalVisibleRect(rect);JSONObject result=new JSONObject().put("shown",shown).put("bounds",new JSONArray(new int[]{rect.left,rect.top,rect.right,rect.bottom})).put("padding",new JSONArray(new int[]{view.getPaddingLeft(),view.getPaddingTop(),view.getPaddingRight(),view.getPaddingBottom()}));
        if(view instanceof TextView){TextView text=(TextView)view;JSONArray icons=new JSONArray();for(Drawable icon:text.getCompoundDrawables())icons.put(icon==null?JSONObject.NULL:new JSONArray(new int[]{icon.getIntrinsicWidth(),icon.getIntrinsicHeight()}));result.put("font_px",text.getTextSize()).put("scale_x",text.getTextScaleX()).put("font_padding",text.getIncludeFontPadding()).put("gravity",text.getGravity()).put("typeface",System.identityHashCode(text.getTypeface())).put("compound_padding",text.getCompoundDrawablePadding()).put("icons",icons);}
        return result;
    }
}

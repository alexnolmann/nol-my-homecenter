package local.mediacentre.client.ui;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;
import local.mediacentre.client.R;

/** Geometry, list positions and button contrast only: no entered text, device IDs or media URLs. */
public final class UiAuditProbe {
    private static int systemUi=-1;
    private static final Set<String> ROLES=new HashSet<>(Arrays.asList("header_clock","audio_output","mini_player_state","mini_player_toggle","mini_player_volume","station_search","station_list","weather_week","finance_shares_scroll","music_current","music_volume","music_position","settings_title","settings_display_scroll","settings_connection_scroll","settings_audio_scroll","photoframe_start","theme_light"));
    public static void run(Activity a,String action)throws Exception{
        View root=a.findViewById(R.id.panel_root);View theme=PanelStyle.find(root,"theme_light");
        if("system_bars".equals(action)){View decor=a.getWindow().getDecorView();if(systemUi<0)systemUi=decor.getSystemUiVisibility();decor.setSystemUiVisibility(systemUi&~(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY));}
        else if("restore_bars".equals(action)){if(systemUi>=0)a.getWindow().getDecorView().setSystemUiVisibility(systemUi);systemUi=-1;}
        else if("pressed".equals(action)||"release".equals(action)){if(theme==null)throw new IllegalStateException("Open settings first");theme.setPressed("pressed".equals(action));}
        else if(!"snapshot".equals(action))throw new IllegalArgumentException("Unknown audit action");
        JSONObject result=new JSONObject();JSONArray controls=new JSONArray(),lists=new JSONArray(),scrolls=new JSONArray();walk(root,controls,lists,scrolls);
        result.put("controls",controls).put("lists",lists).put("scrolls",scrolls).put("dark",new PanelStyle(a).palette.dark);
        if(theme instanceof TextView){TextView text=(TextView)theme;Drawable face=text.getBackground();Bitmap sample=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);Rect original=new Rect(face.getBounds());int color;
            try{face.setBounds(0,0,8,8);face.draw(new Canvas(sample));color=sample.getPixel(4,4);}finally{face.setBounds(original);sample.recycle();}
            result.put("theme_button",new JSONObject().put("pressed",theme.isPressed()).put("foreground",text.getCurrentTextColor()).put("background",color).put("contrast",contrast(text.getCurrentTextColor(),color)));
        }
        File dir=a.getExternalFilesDir(null);try(OutputStream out=new FileOutputStream(new File(dir,"ui-audit.json"))){out.write(result.toString().getBytes("UTF-8"));}
    }
    private static String role(View v){
        String value=v.getContentDescription()==null?"":v.getContentDescription().toString();if(ROLES.contains(value))return value;
        if(v.getId()!=View.NO_ID)try{value=v.getResources().getResourceEntryName(v.getId());if(ROLES.contains(value)||"content_scroll".equals(value))return value;}catch(Exception ignored){}
        return "";
    }
    private static JSONObject bounds(View v)throws Exception{
        int[] p=new int[2];v.getLocationOnScreen(p);Rect clip=new Rect();boolean shown=v.getGlobalVisibleRect(clip);
        JSONObject value=new JSONObject().put("role",role(v)).put("bounds",new JSONArray(new int[]{p[0],p[1],p[0]+v.getWidth(),p[1]+v.getHeight()})).put("visible",shown?new JSONArray(new int[]{clip.left,clip.top,clip.right,clip.bottom}):JSONObject.NULL).put("identity",System.identityHashCode(v));
        if(v instanceof TextView)value.put("font_px",((TextView)v).getTextSize());return value;
    }
    private static void walk(View v,JSONArray controls,JSONArray lists,JSONArray scrolls)throws Exception{
        if(v.getVisibility()!=View.VISIBLE||!v.isShown())return;
        String role=role(v);if(!role.isEmpty())controls.put(bounds(v));
        if(v instanceof ListView){ListView list=(ListView)v;lists.put(bounds(v).put("count",list.getCount()).put("first",list.getFirstVisiblePosition()).put("top",list.getChildCount()==0?0:list.getChildAt(0).getTop()));}
        if(v instanceof ScrollView||v instanceof HorizontalScrollView)scrolls.put(bounds(v).put("x",v.getScrollX()).put("y",v.getScrollY()));
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int n=0;n<group.getChildCount();n++)walk(group.getChildAt(n),controls,lists,scrolls);}
    }
    private static double luminance(int color){double sum=0;int[] channels={Color.red(color),Color.green(color),Color.blue(color)};double[] weights={.2126,.7152,.0722};for(int n=0;n<3;n++){double v=channels[n]/255d;sum+=weights[n]*(v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4));}return sum;}
    private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
}

package local.mediacentre.client.ui;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;
import local.mediacentre.client.R;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.presentation.*;

/** In-memory layout fixtures only. Never writes preferences, the API cache or server data. */
public final class FinanceLayoutProbe {
    private static ScreenModel original;
    public static void run(Activity a,String mode)throws Exception{
        Field field=MainActivity.class.getDeclaredField("financePaletteModel");field.setAccessible(true);
        if("snapshot".equals(mode)){
            View root=a.findViewById(R.id.content);HorizontalScrollView strip=(HorizontalScrollView)find(root,"finance_shares_scroll");
            JSONObject result=new JSONObject().put("scroll_x",strip.getScrollX()).put("viewport",strip.getWidth()).put("strip_width",strip.getChildAt(0).getWidth());
            JSONArray changes=new JSONArray();collect(root,changes);result.put("changes",changes);
            File dir=a.getExternalFilesDir(null);if(dir==null)throw new IOException("No diagnostic directory");try(OutputStream out=new FileOutputStream(new File(dir,"finance-layout-snapshot.json"))){out.write(result.toString().getBytes("UTF-8"));}return;
        }
        Method render=MainActivity.class.getDeclaredMethod("render",ScreenModel.class);render.setAccessible(true);
        if("reset".equals(mode)){if(original!=null){render.invoke(a,original);original=null;}return;}
        if(!"edge".equals(mode)&&!"empty".equals(mode))throw new IllegalArgumentException("Unknown fixture");
        if(original==null)original=(ScreenModel)field.get(a);if(original==null||original.finance==null)throw new IllegalStateException("Open finance first");
        JSONArray items=new JSONArray();
        if("edge".equals(mode)){
            items.put(item("USD","currency","99999.995","-0.12")).put(item("EUR","currency",null,null)).put(item("CNY","currency","12.345","0.00"));
            for(int n=0;n<7;n++)items.put(item("TEST"+n,"share",n==1?null:n==2?"12345678.99":"123.45",n==1?null:n%2==0?"1.25":"-0.75"));
        }
        JSONObject meta=new JSONObject().put("test_data",true).put("scenario","visual_fixture").put("freshness","fresh");
        Dtos.Finance finance=new Dtos.Finance(new JSONObject().put("meta",meta).put("data",new JSONObject().put("items",items)));
        render.invoke(a,Presenters.finance(finance));TextView title=(TextView)find(a.findViewById(R.id.content),"finance_title");if(title!=null)title.setText("ТЕСТ");
    }
    private static JSONObject item(String symbol,String kind,String price,String change)throws Exception{
        return new JSONObject().put("symbol",symbol).put("kind",kind).put("source","visual_fixture").put("status","fresh").put("price_kind","currency".equals(kind)?"published_rate":"last_trade")
            .put("price",price==null?JSONObject.NULL:price).put("previous_price",price==null?JSONObject.NULL:"100.00").put("change_percent",change==null?JSONObject.NULL:change)
            .put("comparison","currency".equals(kind)?"previous_published_rate":"previous_close").put("reference_date","2026-10-22");
    }
    private static View find(View v,String description){if(description.equals(v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){View hit=find(g.getChildAt(n),description);if(hit!=null)return hit;}}return null;}
    private static void collect(View v,JSONArray result)throws Exception{
        if(v instanceof TextView&&v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith("finance_change_")){TextView t=(TextView)v;android.graphics.drawable.Drawable icon=t.getCompoundDrawables()[0];result.put(new JSONObject().put("id",t.getContentDescription().toString()).put("icon_width",icon==null?0:icon.getIntrinsicWidth()).put("text_width",t.getPaint().measureText(t.getText().toString())).put("available",t.getWidth()-t.getCompoundPaddingLeft()-t.getCompoundPaddingRight()));}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++)collect(g.getChildAt(n),result);}
    }
}

package local.mediacentre.client.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import local.mediacentre.client.data.ApiClient;
import org.json.JSONArray;
import org.json.JSONObject;

/** Fixed dashboard tile: retain the displayed bitmap until its replacement has decoded. */
final class HomePhotoPreview {
    private static final int LIMIT=4;
    private static final class Photo {
        final String url;final Bitmap bitmap;
        Photo(String url,Bitmap bitmap){this.url=url;this.bitmap=bitmap;}
    }
    private final Activity activity;
    private final Handler ui=new Handler();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<Photo> thumbnails=new ArrayList<>();
    private final Runnable open;
    private final PanelStyle style;
    private final LinearLayout card;
    private final ImageView image;
    private final int height;
    private final boolean posterHome;
    private final boolean posterFrame;
    private boolean previewFill=true;
    private LinearLayout strip;
    private int current;
    private boolean loading;
    private volatile boolean closed;
    private final Runnable rotate=new Runnable(){public void run(){if(closed||thumbnails.size()<2)return;current=(current+1)%thumbnails.size();present(thumbnails.get(current));ui.postDelayed(this,8000);}};

    HomePhotoPreview(Activity activity,LinearLayout row,Runnable open){
        this(activity,row,open,224);
    }
    HomePhotoPreview(Activity activity,LinearLayout row,Runnable open,int height){this(activity,row,open,height,false);}
    HomePhotoPreview(Activity activity,LinearLayout row,Runnable open,int height,boolean filmstrip){
        this.height=height;posterHome=row instanceof PosterHomeView.PhotoSlot;posterFrame=row instanceof PosterPhotoFrameView.PreviewSlot;
        this.activity=activity;this.open=open;style=new PanelStyle(activity);
        card=style.card();if(posterHome||posterFrame){card.setPadding(0,0,0,0);style.bind(card,p->card.setBackgroundColor(android.graphics.Color.TRANSPARENT));}else card.setPadding(style.dp(8),style.dp(8),style.dp(8),style.dp(8));card.setContentDescription("open_photoframe");card.setFocusable(true);card.setOnClickListener(v->{if(!posterFrame||!thumbnails.isEmpty())open.run();});
        image=posterFrame?new PosterPhotoFrameView.PreviewImage(activity):style.poster&&!posterHome?new PosterPhotoView(activity,PosterPhotoView.ANGLED):new ImageView(activity);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(image,filmstrip?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(-1,-1));
        if(filmstrip){strip=new LinearLayout(activity);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,style.dp(66));sp.topMargin=style.dp(8);card.addView(strip,sp);}
        if(posterFrame&&strip!=null)card.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)strip.getLayoutParams();int size=Math.round((b-t)*.17f),gap=Math.round((b-t)*.012f);if(p.height!=size||p.topMargin!=gap){p.height=size;p.topMargin=gap;strip.setLayoutParams(p);}});
        style.bind(image,p->{image.setBackgroundColor(p.color(PanelStyle.SOFT));if(thumbnails.isEmpty()){image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);image.setImageDrawable(new PanelIcon("photoframe",p.color(PanelStyle.MUTED),p.dp(64)));}});
        attach(row);
    }

    /** Called synchronously during dashboard rendering, before any network result. */
    void attach(LinearLayout row){
        if(closed)return;
        if(card.getParent() instanceof ViewGroup)((ViewGroup)card.getParent()).removeView(card);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,posterHome||posterFrame?-1:style.dp(height),1);params.leftMargin=style.dp(style.poster?0:20);row.addView(card,params);
        new PanelStyle(activity).repaint(card);
        refresh();
    }

    private void refresh(){
        if(closed||loading)return;loading=true;
        final ArrayList<Photo> previous=new ArrayList<>(thumbnails);
        worker.execute(()->load(previous));
    }

    private void load(ArrayList<Photo> previous){
        try{
            SharedPreferences connection=activity.getSharedPreferences("connection",Activity.MODE_PRIVATE);
            ApiClient api=new ApiClient(activity,connection.getString("base_url",""),connection.getString("token",""),connection.getString("server_id",null));
            ApiClient.Response response=api.fetch("/api/v1/plugins/photoframe/manifest",null);
            if(response.code!=200||closed)return;
            JSONArray items=new JSONObject(response.body).getJSONObject("data").getJSONArray("items");
            if(items.length()==0){ui.post(()->apply(new ArrayList<>()));return;}
            ArrayList<Photo> ready=new ArrayList<>();
            for(int index=0;index<items.length()&&ready.size()<(posterFrame?5:LIMIT)&&!closed;index++){
                JSONObject item=items.getJSONObject(index);String id=item.getString("id"),url=item.getString("url");
                if(!id.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")||!url.matches("/api/v1/plugins/photoframe/photos/"+id+"/[a-f0-9]{64}"))continue;
                Photo cached=null;for(Photo photo:previous)if(photo.url.equals(url)){cached=photo;break;}
                if(cached!=null){ready.add(cached);continue;}
                try{
                    byte[] bytes=api.photo(url);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth<1||bounds.outHeight<1)continue;
                    BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(bounds.outWidth/options.inSampleSize>(posterFrame?1400:posterHome?1000:600)||bounds.outHeight/options.inSampleSize>(posterFrame?1000:posterHome?1000:400))options.inSampleSize*=2;
                    Bitmap thumb=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(thumb!=null)ready.add(new Photo(url,thumb));
                }catch(Exception ignored){} // Keep displaying the previous image while a replacement is unavailable.
            }
            if(!ready.isEmpty()&&!closed)ui.post(()->apply(ready));
        }catch(Exception ignored){} // An offline/failed refresh never removes the tile or the current image.
        finally{ui.post(()->loading=false);}
    }

    private void apply(ArrayList<Photo> ready){
        if(closed)return;
        boolean unchanged=ready.size()==thumbnails.size();
        if(unchanged)for(int n=0;n<ready.size();n++)if(!ready.get(n).url.equals(thumbnails.get(n).url)){unchanged=false;break;}
        if(unchanged)return;
        String displayed=thumbnails.isEmpty()?null:thumbnails.get(current).url;
        ui.removeCallbacks(rotate);thumbnails.clear();thumbnails.addAll(ready);current=0;
        if(thumbnails.isEmpty()){updateStrip();new PanelStyle(activity).repaint(image);return;}
        for(int n=0;n<thumbnails.size();n++)if(thumbnails.get(n).url.equals(displayed)){current=n;break;}
        updateStrip();present(thumbnails.get(current));if(thumbnails.size()>1)ui.postDelayed(rotate,8000);
    }

    private void updateStrip(){if(strip==null)return;strip.removeAllViews();for(int n=0;n<thumbnails.size();n++){final int index=n;ImageView thumb=new ImageView(activity);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);thumb.setImageBitmap(thumbnails.get(n).bitmap);thumb.setContentDescription("photoframe_preview_"+n);thumb.setPadding(style.dp(2),style.dp(2),style.dp(2),style.dp(2));thumb.setOnClickListener(v->{current=index;present(thumbnails.get(index));});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(n>0)p.leftMargin=style.dp(4);strip.addView(thumb,p);}}
    void fill(boolean value){if(!posterFrame)return;previewFill=value;if(!thumbnails.isEmpty())present(thumbnails.get(current));}
    private void present(Photo photo){image.setScaleType(posterFrame&&!previewFill?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP);if(posterFrame)image.setBackgroundColor(previewFill?(style.palette.dark?0xff202d43:0xffe9edf0):android.graphics.Color.BLACK);image.setImageBitmap(photo.bitmap);if(strip!=null)for(int n=0;n<strip.getChildCount();n++){View thumb=strip.getChildAt(n);thumb.setSelected(n==current);thumb.setBackgroundColor(n==current?(posterFrame?(style.palette.dark?style.color(PanelStyle.SIGNAL):0xffff4b12):new PanelStyle(activity).color(PanelStyle.SIGNAL)):android.graphics.Color.TRANSPARENT);}}
    void close(){closed=true;ui.removeCallbacksAndMessages(null);worker.shutdownNow();}
}

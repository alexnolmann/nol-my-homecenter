package local.mediacentre.client.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;
import local.mediacentre.client.R;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.presentation.PosterLabels;
import local.mediacentre.client.radio.*;

/** Shared light/dark poster shell and a recycling native list with six full rows. */
final class PosterStationsView extends PosterPageView {
    private static final float WIDTH=1469,HEIGHT=992;
    private final boolean dark;
    private final int ink,muted,blue,signal,background,filterSurface,selectedSurface,rule,accent,playSurface,pressed,disabled,onSignal,trackColor,thumbColor;
    private final ArrayList<Box> boxes=new ArrayList<>();
    private final ArrayList<Borrowed> borrowed=new ArrayList<>();
    private final View tools,miniRoot,statusRoot;
    private final int toolsVisibility,miniVisibility,statusVisibility;
    private final ListView list;
    private final Typeface latin;
    private final CatalogueAdapter adapter=new CatalogueAdapter();
    private final TextView empty;
    private final Thumbnail footerArt;
    private final HashMap<String,Boolean> favorites=new HashMap<>();
    private final HashSet<String> pendingFavorites=new HashSet<>();
    private final ArrayList<Dtos.Station> stations=new ArrayList<>();
    private StationsScreen.Actions actions;
    private int rowHeight;
    private float scaleY=1;
    private boolean loading;
    private String footerStationId;
    private final EditText search;
    private AppearanceState pendingAppearance;
    static final class AppearanceState {
        final int first,top;final boolean focused;final int start,end;
        AppearanceState(ListView list,EditText search){first=list.getFirstVisiblePosition();top=list.getChildCount()==0?0:list.getChildAt(0).getTop();focused=search.hasFocus();start=search.getSelectionStart();end=search.getSelectionEnd();}
    }
    AppearanceState appearance(){return pendingAppearance!=null?pendingAppearance:new AppearanceState(list,search);}
    void appearance(AppearanceState state){pendingAppearance=state;if(!loading)restoreAppearance();}
    private void restoreAppearance(){
        final AppearanceState state=pendingAppearance;if(state==null)return;
        if(loading||rowHeight==0||list.getWidth()==0)return;
        list.post(()->{
            if(pendingAppearance!=state||!isAttachedToWindow()||loading)return;
            list.setSelectionFromTop(Math.min(state.first,Math.max(0,adapter.getCount()-1)),state.top);
            if(state.focused){search.requestFocus();search.setSelection(Math.max(0,Math.min(state.start,search.length())),Math.max(0,Math.min(state.end,search.length())));}
            // First measurement notifies the adapter on API 19. Keep the anchor until that layout is drawn,
            // including an empty or shortened result, without repeatedly requesting layout afterwards.
            list.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
                public boolean onPreDraw(){list.getViewTreeObserver().removeOnPreDrawListener(this);if(pendingAppearance==state)pendingAppearance=null;return true;}
            });
        });
    }

    private static final class Box {final View view;final float x,y,w,h,font;Box(View v,float x,float y,float w,float h,float font){view=v;this.x=x;this.y=y;this.w=w;this.h=h;this.font=font;}}
    private static final class Borrowed {
        final View view;final ViewGroup parent;final ViewGroup.LayoutParams params;final int index;final Drawable background,thumb,track;final int thumbOffset;
        final Typeface face;final float font,scale;final int gravity;final boolean padding;final ColorStateList colors;final Drawable[] compounds;final int compoundPadding;final int[] insets;
        Borrowed(View v){view=v;parent=(ViewGroup)v.getParent();params=v.getLayoutParams();index=parent.indexOfChild(v);background=v.getBackground();insets=new int[]{v.getPaddingLeft(),v.getPaddingTop(),v.getPaddingRight(),v.getPaddingBottom()};
            if(v instanceof TextView){TextView t=(TextView)v;face=t.getTypeface();font=t.getTextSize();scale=t.getTextScaleX();gravity=t.getGravity();padding=t.getIncludeFontPadding();colors=t.getTextColors();compounds=t.getCompoundDrawables();compoundPadding=t.getCompoundDrawablePadding();}else{face=null;font=scale=0;gravity=compoundPadding=0;padding=false;colors=null;compounds=null;}
            if(v instanceof SeekBar){SeekBar b=(SeekBar)v;thumb=b.getThumb();track=b.getProgressDrawable();thumbOffset=b.getThumbOffset();}else{thumb=track=null;thumbOffset=0;}parent.removeView(v);}
        void restore(){((ViewGroup)view.getParent()).removeView(view);parent.addView(view,index,params);view.setBackground(background);view.setPadding(insets[0],insets[1],insets[2],insets[3]);if(view instanceof TextView){TextView t=(TextView)view;t.setTypeface(face);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,font);t.setTextScaleX(scale);t.setGravity(gravity);t.setIncludeFontPadding(padding);t.setTextColor(colors);t.setCompoundDrawables(compounds[0],compounds[1],compounds[2],compounds[3]);t.setCompoundDrawablePadding(compoundPadding);}if(view instanceof SeekBar){SeekBar b=(SeekBar)view;b.setProgressDrawable(track);b.setThumb(thumb);b.setThumbOffset(thumbOffset);}new PanelStyle(view.getContext()).repaint(view);}
    }
    PosterStationsView(Context c,EditText search,Button genre,CheckBox favoriteFilter,View tools,
            TextClock clock,AudioOutputControls output,View miniRoot,TextView miniName,View waveform,Button toggle,
            VolumeButton mute,SeekBar slider,TextView volume,View statusRoot){
        super(c);this.search=search;latin=Typeface.createFromAsset(c.getAssets(),"fonts/Anton-Regular.ttf");PanelStyle s=new PanelStyle(c);dark=s.palette.dark;
        ink=dark?s.color(PanelStyle.TEXT):0xff050d15;muted=dark?0xffb8c9df:0xff536274;blue=dark?0xff16358a:0xff0634e8;signal=dark?s.color(PanelStyle.SIGNAL):0xffff4b12;
        background=s.color(PanelStyle.BG);filterSurface=dark?0xff141d2d:0xffe9e9e9;selectedSurface=dark?0xff1b2d49:0xffe5eeff;rule=dark?0xff43566e:0xffbec5ce;accent=dark?s.color(PanelStyle.ACCENT):blue;
        playSurface=dark?0xff2548b8:blue;pressed=dark?0xff315de0:0xff153cb5;disabled=dark?s.color(PanelStyle.DISABLED):0xffaeb8c6;onSignal=dark?s.color(PanelStyle.ON_SIGNAL):Color.WHITE;trackColor=dark?0xff3b4d66:0xffc8cdd4;thumbColor=dark?0xffc3d2f4:0xff778493;
        this.tools=tools;toolsVisibility=tools.getVisibility();tools.setVisibility(GONE);this.miniRoot=miniRoot;miniVisibility=miniRoot.getVisibility();miniRoot.setVisibility(GONE);this.statusRoot=statusRoot;statusVisibility=statusRoot.getVisibility();statusRoot.setVisibility(GONE);
        place(new Backdrop(c),0,0,1469,992,0);
        TextView home=label(c.getSharedPreferences("connection",0).getString("display_title","Мой дом"),ink,true);place(home,24,16,650,30,24);
        TextView title=label("Станции",ink,true);title.setTypeface(PosterTypography.face(c));title.setTextScaleX(.84f);title.setContentDescription("stations_title");place(title,24,43,720,132,140);
        PosterAudioHeader.bind(this::take,clock,output);
        take(search,24,191,594,60,24);search.setTextColor(ink);search.setHintTextColor(muted);search.setBackground(surface(filterSurface,3));search.setPadding(s.dp(14),0,s.dp(12),0);search.setCompoundDrawablePadding(s.dp(12));search.setCompoundDrawablesWithIntrinsicBounds(new PanelIcon("search",muted,s.dp(28)),null,null,null);
        take(genre,639,191,240,60,24);genre.setTextColor(ink);genre.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));genre.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);genre.setPadding(s.dp(14),0,s.dp(10),0);genre.setCompoundDrawablesWithIntrinsicBounds(null,null,new PanelIcon("chevron_down",ink,s.dp(22)),null);genre.setCompoundDrawablePadding(s.dp(8));genre.setBackground(surface(filterSurface,3));
        take(favoriteFilter,916,191,305,60,24);favoriteFilter.setTextColor(ink);favoriteFilter.setBackgroundColor(background);
        list=new ListView(c);list.setContentDescription("station_list");list.setDivider(null);list.setCacheColorHint(Color.TRANSPARENT);list.setSelector(new ColorDrawable(Color.TRANSPARENT));list.setVerticalScrollBarEnabled(false);list.setAdapter(adapter);list.setItemsCanFocus(true);place(list,24,270,1188,576,0);
        empty=label("Станции не найдены\nИзмените поиск или добавьте станции в веб-панели.",muted,false);empty.setSingleLine(false);empty.setGravity(Gravity.CENTER);place(empty,24,270,1188,576,24);list.setEmptyView(empty);
        footerArt=new Thumbnail(c);place(footerArt,14,912,63,63,0);
        PosterMiniPlayer.bind(c,this::take,miniName,waveform,(Button)toggle,mute,slider,volume,0);
    }
    void catalogue(Dtos.Catalogue data,StationsScreen.Actions actions,boolean filtered){
        this.actions=actions;boolean reset=loading;loading=false;empty.setText("Станции не найдены\nИзмените поиск или добавьте станции в веб-панели.");int first=reset?0:list.getFirstVisiblePosition(),top=reset||list.getChildCount()==0?0:list.getChildAt(0).getTop();String anchor=!reset&&first<stations.size()?stations.get(first).id:null;
        stations.clear();stations.addAll(data.stations);if(!data.meta.offline)favorites.clear();adapter.notifyDataSetChanged();int index=0;if(anchor!=null)for(int n=0;n<stations.size();n++)if(stations.get(n).id.equals(anchor)){index=n;break;}list.setSelectionFromTop(index,top);list.setAlpha(1);tick();restoreAppearance();
    }
    void loading(){loading=true;list.setAlpha(.45f);empty.setText("Поиск станций…");for(int n=0;n<list.getChildCount();n++){StationRow row=(StationRow)list.getChildAt(n);row.labels.setEnabled(false);row.star.setEnabled(false);row.play.setEnabled(false);}}
    void favoriteChanged(String id,boolean value,boolean filtered){
        pendingFavorites.remove(id);favorites.put(id,value);if(filtered&&!value){int first=list.getFirstVisiblePosition(),top=list.getChildCount()==0?0:list.getChildAt(0).getTop();for(Iterator<Dtos.Station> it=stations.iterator();it.hasNext();)if(it.next().id.equals(id)){it.remove();break;}adapter.notifyDataSetChanged();list.setSelectionFromTop(Math.min(first,Math.max(0,stations.size()-1)),top);}else for(int n=0;n<list.getChildCount();n++){StationRow row=(StationRow)list.getChildAt(n);if(row.station.id.equals(id))row.favorite(value);}
    }
    void favoriteFailed(String id){pendingFavorites.remove(id);for(int n=0;n<list.getChildCount();n++){StationRow row=(StationRow)list.getChildAt(n);if(row.station.id.equals(id))row.favorite(favorites.containsKey(id)?favorites.get(id):row.station.favorite);}}
    void tick(){
        for(int n=0;n<list.getChildCount();n++)((StationRow)list.getChildAt(n)).state();RadioStation saved=RadioStore.saved(getContext());Dtos.Station current=null;if(saved!=null)for(Dtos.Station item:stations)if(saved.id.equals(item.id)){current=item;break;}
        if(current!=null){footerArt.station(current);footerStationId=current.id;}else if(saved==null||!saved.id.equals(footerStationId)){footerArt.station(null);footerStationId=saved==null?null:saved.id;}
    }
    void restore(){for(int n=0;n<list.getChildCount();n++)((StationRow)list.getChildAt(n)).wave.setPlaying(false);for(int n=borrowed.size()-1;n>=0;n--)borrowed.get(n).restore();borrowed.clear();tools.setVisibility(toolsVisibility);miniRoot.setVisibility(miniVisibility);statusRoot.setVisibility(statusVisibility);}
    private void take(View v,float x,float y,float w,float h,float font){borrowed.add(new Borrowed(v));place(v,x,y,w,h,font);}
    private void place(View v,float x,float y,float w,float h,float font){boxes.add(new Box(v,x,y,w,h,font));addView(v);}
    private GradientDrawable surface(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(new PanelStyle(getContext()).dp(radius));return d;}
    private TextView label(String text,int color,boolean bold){TextView t=new TextView(getContext());t.setText(text);t.setTextColor(color);t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));t.setSingleLine();t.setEllipsize(TextUtils.TruncateAt.END);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=pageHeight(ws,hs);setMeasuredDimension(w,h);float sx=w/WIDTH;scaleY=h/HEIGHT;int next=Math.max(1,Math.round(96*scaleY));if(next!=rowHeight){rowHeight=next;adapter.notifyDataSetChanged();}for(Box b:boxes){if(b.font>0)((TextView)b.view).setTextSize(TypedValue.COMPLEX_UNIT_PX,b.font*Math.min(sx,scaleY));b.view.measure(MeasureSpec.makeMeasureSpec(Math.round(b.w*sx),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((b.view==list?rowHeight*6:Math.round(b.h*scaleY)),MeasureSpec.EXACTLY));}}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){float sx=getWidth()/WIDTH;for(Box item:boxes){int x=Math.round(item.x*sx),y=Math.round(item.y*scaleY);item.view.layout(x,y,x+item.view.getMeasuredWidth(),y+item.view.getMeasuredHeight());}if(pendingAppearance!=null)restoreAppearance();}
    private final class CatalogueAdapter extends BaseAdapter {
        public int getCount(){return stations.size();}public Object getItem(int n){return stations.get(n);}public boolean hasStableIds(){return true;}public long getItemId(int n){long hash=0xcbf29ce484222325L;for(char c:stations.get(n).id.toCharArray())hash=(hash^c)*0x100000001b3L;return hash;}
        public View getView(int position,View recycled,ViewGroup parent){StationRow row=recycled instanceof StationRow?(StationRow)recycled:new StationRow(getContext());row.setLayoutParams(new AbsListView.LayoutParams(-1,rowHeight));row.bind(stations.get(position));return row;}
    }
    private final class StationRow extends ViewGroup {
        final Thumbnail art;final LinearLayout labels;final TextView name,genre;final RadioEqualizerView wave;final StarButton star;final RowPlay play;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Dtos.Station station;boolean selected,active;String drawn="";
        StationRow(Context c){super(c);setWillNotDraw(false);setBackgroundColor(background);art=new Thumbnail(c);addView(art);labels=new LinearLayout(c);labels.setOrientation(LinearLayout.VERTICAL);labels.setGravity(Gravity.CENTER_VERTICAL);name=label("",ink,true);name.setTypeface(PosterTypography.face(c));genre=label("",muted,false);labels.addView(name,new LinearLayout.LayoutParams(-1,0,3));labels.addView(genre,new LinearLayout.LayoutParams(-1,0,2));addView(labels);labels.setOnClickListener(v->{if(station.enabled&&actions!=null)actions.select(station);});wave=new RadioEqualizerView(c);wave.setColor(signal);addView(wave);star=new StarButton(c);star.setBackgroundColor(Color.TRANSPARENT);star.setOnClickListener(v->{if(actions!=null){pendingFavorites.add(station.id);star.setEnabled(false);actions.favorite(station,star);}});addView(star);play=new RowPlay(c);play.setBackgroundColor(Color.TRANSPARENT);play.setOnClickListener(v->{if(actions!=null)actions.play(station);});addView(play);}
        void bind(Dtos.Station value){station=value;drawn="";setContentDescription("station_row_"+station.id);labels.setContentDescription("select_station_"+station.id);name.setTypeface(station.name.matches(".*[А-Яа-яЁё].*")?PosterTypography.face(getContext()):latin);PanelStyle.update(name,PosterLabels.station(station.name));String quality=PosterLabels.quality(station.name);PanelStyle.update(genre,station.genre+(quality.isEmpty()?"":" · "+quality)+(station.enabled?"":" · отключена"));labels.setEnabled(station.enabled&&!loading);labels.setAlpha(station.enabled?1f:.55f);art.station(station);star.setContentDescription("favorite_station_"+station.id);favorite(favorites.containsKey(station.id)?favorites.get(station.id):station.favorite);play.setContentDescription("play_station_"+station.id);play.setEnabled(station.enabled&&!loading);state();}
        void favorite(boolean value){star.setTag(value);star.setText(value?"★":"☆");star.setEnabled(!loading&&!pendingFavorites.contains(station.id)&&(station.enabled||value));star.invalidate();}
        void state(){RadioStation saved=RadioStore.saved(getContext());selected=saved!=null&&station.id.equals(saved.id);active=station.id.equals(RadioPlaybackService.stationId());String state=RadioPlaybackService.state(),signature=selected+"/"+active+"/"+state;if(signature.equals(drawn))return;drawn=signature;setSelected(selected||active);setBackgroundColor(selected||active?selectedSurface:background);play.symbol=active?(RadioPlaybackService.starting()?"loading":"stop"):"play";play.active=active;play.invalidate();wave.setContentDescription("station_equalizer_"+station.id);wave.setPlaying(active&&"playing".equals(state));wave.setVisibility(active&&"playing".equals(state)?VISIBLE:INVISIBLE);invalidate();}
        private void measure(View v,int width,int height){v.measure(MeasureSpec.makeMeasureSpec(Math.max(0,width),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(0,height),MeasureSpec.EXACTLY));}
        @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);float sx=w/1188f,sy=h/96f;measure(art,Math.round(112*sx),Math.round(76*sy));measure(labels,Math.round(430*sx),Math.round(66*sy));measure(wave,Math.round(300*sx),Math.round(50*sy));measure(star,Math.round(76*sx),Math.round(80*sy));measure(play,Math.round(80*sx),Math.round(80*sy));name.setTextSize(TypedValue.COMPLEX_UNIT_PX,34*Math.min(sx,sy));genre.setTextSize(TypedValue.COMPLEX_UNIT_PX,20*Math.min(sx,sy));}
        private void at(View v,float x,float y){float sx=getWidth()/1188f,sy=getHeight()/96f;int l=Math.round(x*sx),t=Math.round(y*sy);v.layout(l,t,l+v.getMeasuredWidth(),t+v.getMeasuredHeight());}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){at(art,30,10);at(labels,174,15);at(wave,624,23);at(star,984,8);at(play,1084,8);}
        @Override protected void onDraw(Canvas c){p.setColor(rule);p.setStrokeWidth(1);c.drawLine(0,getHeight()-1,getWidth(),getHeight()-1,p);if(selected||active){p.setColor(accent);c.drawRect(0,0,6*getWidth()/1188f,getHeight(),p);}}
    }
    static final class FavoritesSwitch extends CheckBox {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int ink,accent,off;FavoritesSwitch(Context c){super(c);PanelStyle s=new PanelStyle(c);ink=s.palette.dark?s.color(PanelStyle.TEXT):0xff050d15;accent=s.palette.dark?s.color(PanelStyle.ACCENT):0xff0634e8;off=s.palette.dark?0xff43566e:0xffb9c1cb;setPadding(0,0,0,0);}
        @Override protected void onDraw(Canvas c){int save=c.save();c.scale(getWidth()/305f,getHeight()/60f);star(c,p,29,30,18,true,isChecked()?accent:ink);p.setColor(ink);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(24);Paint.FontMetrics f=p.getFontMetrics();c.drawText(getText().toString(),60,30-(f.ascent+f.descent)/2,p);p.setColor(isChecked()?accent:off);c.drawRoundRect(new RectF(249,15,301,45),15,15,p);p.setColor(Color.WHITE);c.drawCircle(isChecked()?286:264,30,12,p);c.restoreToCount(save);}
    }
    private static void star(Canvas c,Paint p,float x,float y,float radius,boolean filled,int color){Path path=new Path();for(int n=0;n<10;n++){double a=-Math.PI/2+n*Math.PI/5;float r=n%2==0?radius:radius*.46f,xx=x+(float)Math.cos(a)*r,yy=y+(float)Math.sin(a)*r;if(n==0)path.moveTo(xx,yy);else path.lineTo(xx,yy);}path.close();p.setColor(color);p.setStyle(filled?Paint.Style.FILL:Paint.Style.STROKE);p.setStrokeWidth(radius*.10f);p.setStrokeJoin(Paint.Join.ROUND);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
    private final class StarButton extends Button {final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);StarButton(Context c){super(c);setPadding(0,0,0,0);}@Override protected void onDraw(Canvas c){boolean on=Boolean.TRUE.equals(getTag());star(c,p,getWidth()/2f,getHeight()/2f,Math.min(getWidth(),getHeight())*.25f,on,isEnabled()?(on?signal:ink):disabled);}}
    private final class RowPlay extends Button {final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);String symbol="play";boolean active;RowPlay(Context c){super(c);setText("");setPadding(0,0,0,0);}@Override protected void onDraw(Canvas c){float r=Math.min(getWidth(),getHeight())*.47f;p.setColor(!isEnabled()?disabled:isPressed()?pressed:active?signal:playSurface);c.drawCircle(getWidth()/2f,getHeight()/2f,r,p);int size=Math.round(r*1.45f);PanelIcon icon=new PanelIcon(symbol,active?onSignal:Color.WHITE,size);int x=(getWidth()-size)/2,y=(getHeight()-size)/2;icon.setBounds(x,y,x+size,y+size);icon.draw(c);}}
    private static final class Thumbnail extends View {
        static Bitmap river,music;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);Dtos.Station current;String key="";Thumbnail(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        static Bitmap image(Context c,boolean musician){if(musician?music==null:river==null){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=musician?4:2;o.inScaled=false;o.inPreferredConfig=musician?Bitmap.Config.ARGB_8888:Bitmap.Config.RGB_565;Bitmap b=BitmapFactory.decodeResource(c.getResources(),musician?R.drawable.neo_home_music:R.drawable.neo_radio_neva_v2,o);if(musician)music=b;else river=b;}return musician?music:river;}
        void station(Dtos.Station value){String next=value==null?"":value.id+"|"+value.genre+"|"+value.name;if(key.equals(next))return;key=next;current=value;invalidate();}
        @Override protected void onDraw(Canvas c){String words=current==null?"":(current.genre+" "+current.name).toLowerCase(Locale.ROOT);boolean musician=words.contains("jazz")||words.contains("джаз")||words.contains("rock")||words.contains("рок")||words.contains("поп")||words.contains("хит");p.setColor(musician?(words.contains("рок")||words.contains("rock")?0xff872e1c:words.contains("поп")||words.contains("хит")?0xff9b2857:0xff123c5e):0xff122033);c.drawRect(0,0,getWidth(),getHeight(),p);Bitmap b=image(getContext(),musician);float scale=Math.max(getWidth()/(float)b.getWidth(),getHeight()/(float)b.getHeight()),w=b.getWidth()*scale,h=b.getHeight()*scale;c.drawBitmap(b,null,new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2),p);}
    }
    private final class Backdrop extends View {final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final Path mask=new Path();Backdrop(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}@Override protected void onDraw(Canvas c){int save=c.save();c.scale(getWidth()/WIDTH,getHeight()/HEIGHT);p.setColor(background);c.drawRect(0,0,WIDTH,HEIGHT,p);mask.rewind();mask.moveTo(850,112);mask.lineTo(1469,112);mask.lineTo(1469,854);mask.lineTo(1240,854);mask.lineTo(1280,692);mask.lineTo(1212,630);mask.lineTo(1212,282);mask.lineTo(1240,180);mask.lineTo(923,180);mask.close();int clipped=c.save();c.clipPath(mask);Bitmap b=Thumbnail.image(getContext(),false);float scale=Math.max(619f/b.getWidth(),742f/b.getHeight()),w=b.getWidth()*scale,h=b.getHeight()*scale;c.drawBitmap(b,null,new RectF(1319.5f-w/2,483-h/2,1319.5f+w/2,483+h/2),p);if(dark){p.setColor(0x33050d1c);c.drawRect(0,0,WIDTH,HEIGHT,p);}c.restoreToCount(clipped);PosterAudioHeader.draw(c,p,mask,blue);p.setColor(blue);mask.rewind();mask.moveTo(1280,692);mask.lineTo(1469,854);mask.lineTo(1240,854);mask.close();c.drawPath(mask,p);p.setColor(dark?rule:0xff9ca4ae);p.setStrokeWidth(1);c.drawLine(14,900,1447,900,p);c.restoreToCount(save);}}
    private static final class Track extends Drawable {final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int radius;Track(PanelStyle s,int color){p.setColor(color);radius=s.dp(3);}public void draw(Canvas c){Rect b=getBounds();c.drawRoundRect(new RectF(b.left,b.exactCenterY()-radius,b.right,b.exactCenterY()+radius),radius,radius,p);}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}}
    private static final class Thumb extends Drawable {final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int size,radius,color;Thumb(PanelStyle s,int color){this.color=color;size=s.dp(16);radius=s.dp(7);}public void draw(Canvas c){Rect b=getBounds();p.setColor(color);c.drawCircle(b.exactCenterX(),b.exactCenterY(),radius,p);}public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}}
}

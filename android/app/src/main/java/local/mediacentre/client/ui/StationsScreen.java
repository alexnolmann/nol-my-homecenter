package local.mediacentre.client.ui;
import android.content.Context;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.radio.*;
final class StationsScreen {
    interface Actions{void select(Dtos.Station station);void play(Dtos.Station station);void favorite(Dtos.Station station,Button button);}
    private final PanelStyle s;private final List<Entry> rows=new ArrayList<>();
    private PosterStationsView poster;
    private static class Entry{Dtos.Station station;LinearLayout row;TextView state;Button play,favorite;String signature;}
    StationsScreen(Context c){s=new PanelStyle(c);}
    void renderPoster(PosterStationsView view,Dtos.Catalogue data,Actions actions,boolean favoritesOnly){poster=view;view.catalogue(data,actions,favoritesOnly);}
    void render(LinearLayout parent,Dtos.Catalogue data,Actions actions){
        if(data.stations.isEmpty()){LinearLayout empty=s.card();empty.addView(s.title("Станции не найдены",24));empty.addView(s.text("Измените поиск или добавьте станции в веб-панели.",18,PanelStyle.MUTED));parent.addView(empty);return;}
        for(Dtos.Station station:data.stations){
            LinearLayout row=s.card();row.setPadding(s.dp(12),s.dp(8),s.dp(12),s.dp(8));row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(android.view.Gravity.CENTER_VERTICAL);s.bind(row,t->row.setBackground(t.poster?new PosterRule(t.color(row.isSelected()?PanelStyle.SOFT:PanelStyle.BG),t.color(PanelStyle.LINE),t.dp(1)):t.surface(row.isSelected()?PanelStyle.SOFT:PanelStyle.CARD,row.isSelected()?PanelStyle.ACCENT:PanelStyle.SOFT,14)));TextView badge=s.badge(station.name,s.poster?58:48);if(s.poster){badge.setTextSize(25);s.bind(badge,p->{badge.setTextColor(0xffffb658);badge.setBackground(new PosterStationArt());});}LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(s.dp(s.poster?58:48),s.dp(s.poster?58:48));iconParams.rightMargin=s.dp(16);row.addView(badge,iconParams);
            LinearLayout labels=s.column();labels.setContentDescription("select_station_"+station.id);labels.setOnClickListener(v->{if(station.enabled)actions.select(station);});labels.setEnabled(station.enabled);labels.setMinimumHeight(s.dp(56));labels.setAlpha(station.enabled?1f:0.55f);TextView name=s.title(station.name,s.poster?28:20);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);labels.addView(name);TextView genre=s.text(station.genre+(station.enabled?"":" · отключена"),16,PanelStyle.MUTED);genre.setSingleLine(true);genre.setEllipsize(TextUtils.TruncateAt.END);labels.addView(genre);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            Entry entry=new Entry();entry.station=station;entry.row=row;entry.state=s.text("",16,PanelStyle.ACCENT);entry.state.setGravity(android.view.Gravity.CENTER);row.addView(entry.state,new LinearLayout.LayoutParams(s.dp(152),-2));Button star=s.button(station.favorite?"★":"☆","favorite_station_"+station.id,()->{});star.setTag(Boolean.valueOf(station.favorite));if(s.poster){star.setTextSize(34);s.bind(star,p->{star.setTextColor(p.color(Boolean.TRUE.equals(star.getTag())?PanelStyle.SIGNAL:PanelStyle.TEXT));star.setBackgroundColor(android.graphics.Color.TRANSPARENT);});}star.setEnabled(station.enabled||station.favorite);star.setOnClickListener(v->actions.favorite(station,star));entry.favorite=star;LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(s.dp(56),s.dp(56));bp.leftMargin=s.dp(8);row.addView(star,bp);entry.play=s.poster?new MusicTransportButton(s.context,"play"):s.button("Слушать","play_station_"+station.id,()->actions.play(station));if(s.poster){s.bind(entry.play,p->{boolean active=station.id.equals(RadioPlaybackService.stationId());entry.play.setTextColor(p.color(active?PanelStyle.ON_SIGNAL:PanelStyle.ON_ACCENT));entry.play.setBackground(p.surface(active?PanelStyle.SIGNAL:PanelStyle.ACCENT,-1,100));});entry.play.setContentDescription("play_station_"+station.id);entry.play.setOnClickListener(v->actions.play(station));}entry.play.setEnabled(station.enabled);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(s.dp(s.poster?62:112),s.dp(s.poster?62:56));pp.leftMargin=s.dp(12);row.addView(entry.play,pp);rows.add(entry);
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=s.dp(s.poster?0:8);parent.addView(row,rp);
        }tick();
    }
    void favoriteChanged(String id,boolean favorite,boolean favoritesOnly){
        if(poster!=null){poster.favoriteChanged(id,favorite,favoritesOnly);return;}
        for(Iterator<Entry> iterator=rows.iterator();iterator.hasNext();){Entry entry=iterator.next();if(!entry.station.id.equals(id))continue;
            if(favoritesOnly&&!favorite){ViewGroup parent=(ViewGroup)entry.row.getParent();if(parent!=null)parent.removeView(entry.row);iterator.remove();if(rows.isEmpty()){LinearLayout empty=s.card();empty.addView(s.title("Станции не найдены",24));empty.addView(s.text("Измените поиск или добавьте станции в веб-панели.",18,PanelStyle.MUTED));if(parent!=null)parent.addView(empty);}return;}
            entry.favorite.setTag(Boolean.valueOf(favorite));entry.favorite.setText(favorite?"★":"☆");entry.favorite.setEnabled(entry.station.enabled||favorite);return;
        }
    }
    void favoriteFailed(String id){if(poster!=null){poster.favoriteFailed(id);return;}for(Entry entry:rows)if(entry.station.id.equals(id))entry.favorite.setEnabled(entry.station.enabled||Boolean.TRUE.equals(entry.favorite.getTag()));}
    void tick(){if(poster!=null){poster.tick();return;}RadioStation saved=RadioStore.saved(s.context);String playing=RadioPlaybackService.stationId();for(Entry e:rows){boolean selected=saved!=null&&e.station.id.equals(saved.id),active=e.station.id.equals(playing);String label=active?("playing".equals(RadioPlaybackService.state())?"В эфире":"Подключение…"):selected?"Выбрана":"";String signature=label+selected;if(signature.equals(e.signature))continue;e.signature=signature;e.row.setSelected(selected||active);e.state.setText(label);if(e.play instanceof MusicTransportButton)((MusicTransportButton)e.play).symbol(active?"stop":"play");else e.play.setText(active?"Стоп":"Слушать");new PanelStyle(s.context).repaint(e.row);}}
}

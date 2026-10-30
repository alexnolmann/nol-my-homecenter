package local.mediacentre.client.presentation;
import java.util.*;
import local.mediacentre.client.data.Dtos;
public final class ScreenModel {
    public final String title,status;
    public final boolean testData;
    public final List<Row> rows=new ArrayList<>();
    public Dtos.Station playbackStation;
    public Dtos.Dashboard dashboard;
    public Dtos.Weather weather;
    public Dtos.Catalogue radioFavorites;
    public Dtos.Catalogue catalogue;
    public Dtos.Finance finance;
    public ScreenModel(String title,Dtos.Meta m){this.title=title;testData=m.testData;status=(m.offline?"НЕТ СВЯЗИ · сохранённые данные · ":m.revalidating?"Сохранённые данные · проверяем обновление · ":"")+(m.testData?"ТЕСТОВЫЕ ДАННЫЕ · "+m.scenario+" · ":"")+Presenters.freshness(m.expired?"stale":m.freshness)+" · получено "+Presenters.time(m.fetchedAt,"Europe/Moscow")+" МСК";}
    public ScreenModel add(String title,String body){rows.add(new Row(title,body,null));return this;}
    public ScreenModel station(Dtos.Station s){Row row=new Row(s.name,s.genre+(s.favorite?" · Избранное":"")+(s.enabled?"":" · Отключена"),s.enabled?s.id:null);row.station=s;rows.add(row);return this;}
    public static final class Row {public final String title,body,stationId;public Dtos.Station station;Row(String title,String body,String id){this.title=title;this.body=body;stationId=id;}}
}

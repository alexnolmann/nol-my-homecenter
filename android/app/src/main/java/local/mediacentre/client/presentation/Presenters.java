package local.mediacentre.client.presentation;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import local.mediacentre.client.data.Dtos;
public final class Presenters {
    private Presenters(){}
    public static String number(BigDecimal v){return v==null?"—":v.toPlainString();}
    private static String value(String v){return v==null?"—":v;}
    public static String time(String v,String zone){if(v==null)return "—";try{SimpleDateFormat input=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);input.setTimeZone(TimeZone.getTimeZone("UTC"));input.setLenient(false);SimpleDateFormat output=new SimpleDateFormat("dd.MM.yyyy HH:mm",new Locale("ru"));output.setTimeZone(TimeZone.getTimeZone(zone));return output.format(input.parse(v));}catch(Exception error){return "—";}}
    public static String freshness(String s){switch(s){case "fresh":return "снимок готов";case "empty":return "нет данных";case "partial":return "частичные данные";case "stale":return "УСТАРЕЛО";default:return "неизвестное состояние";}}
    public static String condition(String s){switch(s){case "clear":return "Ясно";case "partly_cloudy":return "Переменная облачность";case "cloudy":return "Облачно";case "fog":return "Туман";case "drizzle":return "Морось";case "freezing_rain":return "Ледяной дождь";case "rain":return "Дождь";case "snow":return "Снег";case "thunderstorm":return "Гроза";default:return "Нет описания";}}
    public static ScreenModel weather(Dtos.Weather d){
        ScreenModel s=new ScreenModel("Погода · "+d.city,d.meta);s.weather=d;
        s.add("Сейчас: "+number(d.temperature)+" °C",condition(d.condition)+" · ощущается "+number(d.apparent)+" °C\nВлажность "+number(d.humidity)+" % · ветер "+number(d.wind)+" м/с");
        if(d.days.isEmpty())s.add("Прогноз отсутствует","Данные пока не получены.");
        for(Dtos.Day day:d.days)s.add(day.date+" · "+condition(day.condition),number(day.minimum)+" … "+number(day.maximum)+" °C · осадки "+number(day.rain)+" мм");
        s.add("Источник: "+d.meta.source,"Данные на "+time(d.meta.dataAsOf,d.timezone)+" · получено "+time(d.meta.fetchedAt,d.timezone)+"\n"+d.timezone+" · Open-Meteo.com");return s;
    }
    public static ScreenModel finance(Dtos.Finance d){
        ScreenModel s=new ScreenModel("Финансы · RUB",d.meta);s.finance=d;if(d.items.isEmpty())s.add("Список инструментов пуст","Выберите инструменты в веб-панели.");
        for(Dtos.Instrument i:d.items){
            String title=i.symbol+(i.board==null?" / RUB":" · "+i.board)+": "+number(i.price)+" руб.";
            String body=freshness(i.status)+" · изменение "+number(i.change)+" %\n";
            if(i.kind.equals("currency"))body+="Официальный курс за 1 "+i.symbol+(i.nominal!=null&&i.nominal.compareTo(BigDecimal.ONE)!=0?" · номинал ЦБ: "+number(i.nominal):"")+" · действует "+value(i.rateDate);
            else body+="Последняя сделка: "+time(i.tradeAt,"Europe/Moscow")+" МСК\n"+(i.accessMode.equals("anonymous_delayed")?"Публичный режим · заявленная задержка "+(i.delay==null?"—":i.delay)+" мин":"Режим и задержка данных не подтверждены");
            body+="\n"+(i.comparison.equals("none") || i.referenceDate==null?"Сравнение недоступно":(i.kind.equals("currency")?"Предыдущий опубликованный курс: ":"Предыдущее закрытие: ")+i.referenceDate);
            s.add(title,body+"\nПолучено: "+time(i.fetchedAt,"Europe/Moscow")+" МСК · "+i.source);
        }return s;
    }
    public static ScreenModel home(Dtos.Dashboard d){
        ScreenModel s=new ScreenModel("Главная · "+d.weather.city,d.meta);s.dashboard=d;s.add("Погода: "+number(d.weather.temperature)+" °C",condition(d.weather.condition)+" · "+freshness(d.weather.meta.freshness));
        if(d.finance.items.isEmpty())s.add("Финансы: нет данных","Выберите инструменты в веб-панели.");
        for(Dtos.Instrument i:d.finance.items)s.add(i.symbol+(i.board==null?" / RUB":" · "+i.board)+": "+number(i.price)+" руб.",(i.kind.equals("currency")?"Курс на "+value(i.rateDate):"Сделка "+time(i.tradeAt,"Europe/Moscow")+" МСК")+" · "+freshness(i.status));
        s.add("Радио · избранных: "+d.favorites.size(),"Выберите станцию и нажмите «Слушать».");for(Dtos.Station station:d.favorites)s.station(station);return s;
    }
    public static ScreenModel stations(Dtos.Catalogue d){ScreenModel s=new ScreenModel("Радиостанции · найдено "+d.total,d.meta);s.catalogue=d;if(d.stations.isEmpty())s.add("Станции не найдены","Измените поиск или добавьте станции в веб-панели.");for(Dtos.Station i:d.stations)s.station(i);return s;}
    public static ScreenModel radio(Dtos.Catalogue d,String selectedId){Dtos.Station selected=null;for(Dtos.Station i:d.stations)if(i.enabled&&(selected==null||i.id.equals(selectedId)))selected=i;if(selected==null)return new ScreenModel("Радио · плеер",d.meta).add("Станция не выбрана","Откройте каталог радиостанций.");return radio(selected,d.meta);}
    public static ScreenModel radio(Dtos.Station station,Dtos.Meta meta){ScreenModel s=new ScreenModel("Радио · плеер",meta);s.playbackStation=station;if(!station.enabled)s.add(station.name,"Станция отключена в каталоге.");else s.add(station.name,station.genre+"\nНажмите «Слушать» для прямого эфира.");return s;}
}

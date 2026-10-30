package local.mediacentre.client.presentation;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.data.PanelRepository;
public final class FinancialLabels {
    private FinancialLabels(){}
    public static boolean comparable(Dtos.Instrument i){return i.enabled&&i.price!=null&&i.change!=null&&i.referenceError==null&&i.referenceDate!=null&&("currency".equals(i.kind)?"previous_published_rate":"previous_close").equals(i.comparison);}
    public static String change(Dtos.Instrument i){return comparable(i)?DisplayFormat.change(i.change):"Нет сравнения";}
    public static String price(Dtos.Instrument i){return DisplayFormat.price(i.enabled?i.price:null,i.kind)+" руб.";}
    public static String previousPrice(Dtos.Instrument i){return DisplayFormat.price(comparable(i)?i.previousPrice:null,i.kind)+" руб.";}
    public static String status(Dtos.Instrument i){if(!i.enabled)return "Отключено в панели";if(i.price==null)return i.lastError==null?"Нет цены":"Цена не получена";if("stale".equals(i.status)||i.lastError!=null||PanelRepository.expired(i.validUntil))return "УСТАРЕЛО";if("partial".equals(i.status)||i.referenceError!=null)return "Частичные данные";return "Снимок получен";}
    public static String source(Dtos.Instrument i){if(i.source.equals("cbr")||i.source.equals("cbr (synthetic)")||i.source.equals("CBR (synthetic)"))return "ЦБ РФ";if(i.source.equals("moex_iss")||i.source.equals("moex_iss (synthetic)")||i.source.equals("MOEX ISS (synthetic)"))return "Мосбиржа";return i.source;}
    public static String reference(Dtos.Instrument i){if(!comparable(i))return "Сравнение недоступно";return ("currency".equals(i.kind)?"Предыдущий курс: ":"К закрытию: ")+DisplayFormat.day(i.referenceDate,"dd.MM.yyyy");}
    public static String date(Dtos.Instrument i){if(!i.enabled||i.price==null)return "Дата цены: —";return "currency".equals(i.kind)?"Курс на "+DisplayFormat.day(i.rateDate,"dd.MM.yyyy"):("last_trade".equals(i.priceKind)?"Последняя сделка: ":"Данные на: ")+Presenters.time("last_trade".equals(i.priceKind)?i.tradeAt:i.dataAsOf,"Europe/Moscow")+" МСК";}
    public static String mode(Dtos.Instrument i){return "anonymous_delayed".equals(i.accessMode)&&i.delay!=null?"Заявленная задержка "+i.delay+" мин":"Режим/задержка не подтверждены";}
    public static String fetched(Dtos.Instrument i){return "Получено: "+Presenters.time(i.fetchedAt,"Europe/Moscow")+" МСК";}
}

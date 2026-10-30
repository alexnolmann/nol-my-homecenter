package local.mediacentre.client;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.io.*;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.presentation.FinancialLabels;
public final class FinancialLabelsTest {
    private JSONObject envelope()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=getClass().getResourceAsStream("/success.json")){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        return new JSONObject(out.toString("UTF-8")).getJSONObject("finance");
    }
    private Dtos.Instrument item(JSONObject e,int n)throws Exception{return new Dtos.Finance(e).items.get(n);}
    @Test public void currencyUsesEffectiveDateAndPerUnitDecimalRatherThanFetchTime()throws Exception{
        JSONObject e=envelope(),j=e.getJSONObject("data").getJSONArray("items").getJSONObject(0);
        j.put("price","15.000300000001").put("previous_price","15.1884").put("nominal","10").put("rate_date","2026-09-28").put("fetched_at","2026-09-27T08:00:00Z").put("comparison","previous_published_rate").put("reference_date","2026-09-25").put("change_percent","-1.235");
        Dtos.Instrument i=item(e,0);assertEquals("15,0003 руб.",FinancialLabels.price(i));assertEquals("15,1884 руб.",FinancialLabels.previousPrice(i));assertEquals("Курс на 28.09.2026",FinancialLabels.date(i));assertEquals("Получено: 27.09.2026 11:00 МСК",FinancialLabels.fetched(i));assertEquals("Предыдущий курс: 25.09.2026",FinancialLabels.reference(i));assertEquals("↓ −1,24 %",FinancialLabels.change(i));assertEquals("15.000300000001",i.price.toPlainString());
    }
    @Test public void shareRetainsLastTradeAndDeclaresOnlyConfirmedDelay()throws Exception{
        JSONObject e=envelope(),j=e.getJSONObject("data").getJSONArray("items").getJSONObject(3);
        j.put("price_kind","last_trade").put("trade_at","2026-09-25T15:45:00Z").put("fetched_at","2026-09-27T08:00:00Z").put("access_mode","unknown").put("delay_minutes",15).put("comparison","previous_close").put("reference_date","2026-09-24").put("change_percent","0");
        Dtos.Instrument i=item(e,3);assertEquals("Последняя сделка: 25.09.2026 18:45 МСК",FinancialLabels.date(i));assertEquals("Режим/задержка не подтверждены",FinancialLabels.mode(i));assertEquals("→ 0,00 %",FinancialLabels.change(i));
        j.put("access_mode","anonymous_delayed");assertEquals("Заявленная задержка 15 мин",FinancialLabels.mode(item(e,3)));j.put("delay_minutes",JSONObject.NULL);assertEquals("Режим/задержка не подтверждены",FinancialLabels.mode(item(e,3)));
    }
    @Test public void partialReferenceKeepsPriceButCannotInventComparison()throws Exception{
        JSONObject e=envelope(),j=e.getJSONObject("data").getJSONArray("items").getJSONObject(3);
        j.put("valid_until",JSONObject.NULL).put("status","partial").put("comparison","previous_close").put("reference_date","2026-09-25").put("change_percent","1.25").put("reference_error","REFERENCE_UNAVAILABLE");
        Dtos.Instrument i=item(e,3);assertNotEquals("— руб.",FinancialLabels.price(i));assertEquals("Частичные данные",FinancialLabels.status(i));assertEquals("Нет сравнения",FinancialLabels.change(i));assertEquals("Сравнение недоступно",FinancialLabels.reference(i));
        j.put("reference_error",JSONObject.NULL).put("comparison","unknown");assertFalse(FinancialLabels.comparable(item(e,3)));
        j.put("kind","currency").put("previous_price","123.45");assertEquals("— руб.",FinancialLabels.previousPrice(item(e,3)));
    }
    @Test public void disabledMissingAndStalePricesNeverBecomeZeroOrFresh()throws Exception{
        JSONObject e=envelope(),j=e.getJSONObject("data").getJSONArray("items").getJSONObject(0);
        j.put("valid_until","2000-01-01T00:00:00Z").put("status","fresh");assertEquals("УСТАРЕЛО",FinancialLabels.status(item(e,0)));
        j.put("enabled",false);Dtos.Instrument disabled=item(e,0);assertEquals("Отключено в панели",FinancialLabels.status(disabled));assertEquals("— руб.",FinancialLabels.price(disabled));assertEquals("Дата цены: —",FinancialLabels.date(disabled));assertEquals("Нет сравнения",FinancialLabels.change(disabled));
        j.put("enabled",true).put("price",JSONObject.NULL).put("last_error_code","PROVIDER_TIMEOUT");assertEquals("Цена не получена",FinancialLabels.status(item(e,0)));assertEquals("— руб.",FinancialLabels.price(item(e,0)));
        j.put("last_error_code",JSONObject.NULL);assertEquals("Нет цены",FinancialLabels.status(item(e,0)));
    }
}

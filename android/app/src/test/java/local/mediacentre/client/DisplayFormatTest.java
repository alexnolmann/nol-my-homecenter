package local.mediacentre.client;
import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.*;
import local.mediacentre.client.presentation.DisplayFormat;
public final class DisplayFormatTest {
    @Test public void pricesRoundOnlyForDisplayWithoutLosingStoredPrecision(){
        BigDecimal value=new BigDecimal("12345678901234567890.123456789");
        assertEquals("12345678901234567890,1235",DisplayFormat.price(value,"currency"));
        assertEquals("12345678901234567890,12",DisplayFormat.price(value,"share"));
        assertEquals("12345678901234567890.123456789",value.toPlainString());
        assertEquals("—",DisplayFormat.price(null,"share"));
    }
    @Test public void directionalChangesDistinguishZeroAndMissing(){
        assertEquals("↑ +1,24 %",DisplayFormat.change(new BigDecimal("1.235")));
        assertEquals("↓ −1,24 %",DisplayFormat.change(new BigDecimal("-1.235")));
        assertEquals("→ 0,00 %",DisplayFormat.change(BigDecimal.ZERO));
        assertEquals("—",DisplayFormat.change(null));
    }
    @Test public void weatherKeepsMissingValuesAndCalendarDatesDistinct(){
        assertEquals("—",DisplayFormat.weather(null));assertEquals("-3,3",DisplayFormat.weather(new BigDecimal("-3.25")));assertEquals("0",DisplayFormat.weather(BigDecimal.ZERO));
        assertEquals("27.09",DisplayFormat.day("2026-09-27","dd.MM"));assertEquals("вс",DisplayFormat.day("2026-09-27","EEE"));
        assertEquals("—",DisplayFormat.day("2026-02-29","dd.MM"));assertEquals("—",DisplayFormat.day("2026-09-27junk","dd.MM"));
    }
}

package local.mediacentre.client.presentation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
/** Formatting only: the source BigDecimal and source dates never change. */
public final class DisplayFormat {
    private DisplayFormat(){}
    public static String price(BigDecimal v,String kind){return v==null?"—":v.setScale("currency".equals(kind)?4:2,RoundingMode.HALF_UP).toPlainString().replace('.',',');}
    public static String change(BigDecimal v){if(v==null)return "—";int sign=v.signum();return (sign>0?"↑ +":sign<0?"↓ −":"→ ")+v.abs().setScale(2,RoundingMode.HALF_UP).toPlainString().replace('.',',')+" %";}
    public static String weather(BigDecimal v){return v==null?"—":v.setScale(1,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.',',');}
    public static String day(String value,String pattern){
        if(value==null||!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))return "—";
        try{SimpleDateFormat input=new SimpleDateFormat("yyyy-MM-dd",Locale.US);input.setLenient(false);input.setTimeZone(TimeZone.getTimeZone("UTC"));SimpleDateFormat output=new SimpleDateFormat(pattern,new Locale("ru"));output.setTimeZone(TimeZone.getTimeZone("UTC"));return output.format(input.parse(value));}catch(Exception error){return "—";}
    }
}

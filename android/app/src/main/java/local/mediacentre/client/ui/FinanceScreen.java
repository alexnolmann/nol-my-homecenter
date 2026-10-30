package local.mediacentre.client.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.presentation.FinancialLabels;

final class FinanceScreen {
    private final PanelStyle s;
    FinanceScreen(Context c){s=new PanelStyle(c);}

    void render(LinearLayout parent,Dtos.Finance data,int shareScrollX){
        List<Dtos.Instrument> currencies=new ArrayList<>(),shares=new ArrayList<>();
        for(Dtos.Instrument item:data.items)("currency".equals(item.kind)?currencies:shares).add(item);
        if(data.items.isEmpty()){
            LinearLayout empty=s.card();empty.addView(s.title("Финансовый список пуст",24));empty.addView(s.text("Выберите валюты и акции в веб-панели.",18,PanelStyle.MUTED));parent.addView(empty);return;
        }
        if(!currencies.isEmpty()){parent.addView(s.title("Курсы валют",24));s.gap(parent,12);}
        for(int from=0;from<currencies.size();from+=3){
            LinearLayout row=new LinearLayout(s.context);if(s.poster)s.columns(row);parent.addView(row);
            for(int n=from;n<Math.min(from+3,currencies.size());n++){
                Dtos.Instrument i=currencies.get(n);LinearLayout card=s.card();card.setContentDescription("finance_currency_"+i.symbol);if(s.poster)card.setPadding(s.dp(20),s.dp(6),s.dp(20),s.dp(12));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(n>from)p.leftMargin=s.dp(12);row.addView(card,p);
                card.addView(s.poster?s.title(i.symbol+" / RUB",34):s.text(i.symbol+" / RUB",20,PanelStyle.MUTED));
                TextView price=s.title(s.poster?compact(i.enabled?i.price:null):FinancialLabels.price(i),s.poster?96:36);if(s.poster)PosterTypography.fit(price,96,60);price.setSingleLine(true);price.setEllipsize(android.text.TextUtils.TruncateAt.END);price.setContentDescription("finance_price_"+i.symbol);card.addView(price);
                TextView previous=s.text("Было "+(s.poster?compact(FinancialLabels.comparable(i)?i.previousPrice:null):FinancialLabels.previousPrice(i)),18,PanelStyle.MUTED);previous.setContentDescription("finance_previous_"+i.symbol);LinearLayout details=s.poster?new LinearLayout(s.context):null;if(s.poster){details.setGravity(Gravity.CENTER_VERTICAL);card.addView(details);details.addView(previous,new LinearLayout.LayoutParams(0,-2,1));}else card.addView(previous);
                TextView change=s.poster?s.change(i,26):s.text(FinancialLabels.change(i),20,changeColor(i));change.setContentDescription("finance_change_"+i.symbol);if(s.poster)details.addView(change);else card.addView(change);
            }
            s.gap(parent,16);
        }
        if(!shares.isEmpty()){
            if(s.poster){s.rule(parent);s.gap(parent,10);}
            parent.addView(s.title("Акции Московской биржи"+(s.poster?" · руб.":""),22));s.gap(parent,12);
            HorizontalScrollView scroll=new HorizontalScrollView(s.context);scroll.setHorizontalScrollBarEnabled(false);scroll.setSmoothScrollingEnabled(true);scroll.setContentDescription("finance_shares_scroll");scroll.setTag("finance_shares_scroll");parent.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout row=new LinearLayout(s.context);row.setOrientation(LinearLayout.HORIZONTAL);scroll.addView(row);
            List<LinearLayout> cards=new ArrayList<>();
            for(Dtos.Instrument i:shares){
                LinearLayout card=s.card();card.setGravity(Gravity.CENTER_VERTICAL);card.setContentDescription("finance_share_"+i.symbol);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(s.dp(300),s.dp(172));if(!cards.isEmpty())p.leftMargin=s.dp(12);row.addView(card,p);cards.add(card);
                card.addView(s.poster?s.title(i.symbol,34):s.text(i.symbol,21,PanelStyle.MUTED));
                TextView price=s.title(s.poster?compact(i.enabled?i.price:null):FinancialLabels.price(i),s.poster?54:31);if(s.poster)PosterTypography.fit(price,54,36);price.setSingleLine(true);price.setEllipsize(android.text.TextUtils.TruncateAt.END);price.setContentDescription("finance_price_"+i.symbol);card.addView(price);
                TextView change=s.poster?s.change(i,26):s.text(FinancialLabels.change(i),20,changeColor(i));change.setContentDescription("finance_change_"+i.symbol);card.addView(change);
            }
            scroll.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
                int width=right-left;if(width<=0)return;
                int cardWidth=(width-2*s.dp(12))/3;
                for(LinearLayout card:cards){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)card.getLayoutParams();if(p.width!=cardWidth){p.width=cardWidth;card.setLayoutParams(p);}}
            });
            scroll.post(()->scroll.scrollTo(shareScrollX,0));
        }
    }
    private int changeColor(Dtos.Instrument i){return !FinancialLabels.comparable(i)?PanelStyle.MUTED:i.change.signum()<0?PanelStyle.DOWN:PanelStyle.ACCENT;}
    private static String compact(java.math.BigDecimal value){if(value==null)return "—";java.text.DecimalFormatSymbols symbols=new java.text.DecimalFormatSymbols(java.util.Locale.ROOT);symbols.setDecimalSeparator(',');symbols.setGroupingSeparator(' ');java.text.DecimalFormat format=new java.text.DecimalFormat("#,##0.00",symbols);format.setRoundingMode(java.math.RoundingMode.HALF_UP);return format.format(value);}
}

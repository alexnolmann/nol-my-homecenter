package local.mediacentre.client.ui;
import android.content.Context;
import android.graphics.Canvas;
import android.widget.Button;
/** Uses the button's theme/state tint and redraws only when its transport symbol changes. */
final class MusicTransportButton extends Button {
    private boolean poster,inline;private String symbol;private PanelIcon glyph;private int tint,size;
    MusicTransportButton(Context context,String symbol){super(context);poster=new PanelStyle(context).poster;this.symbol=symbol;setText("");setAllCaps(false);setPadding(0,0,0,0);}
    void design(boolean value){if(poster!=value){poster=value;glyph=null;invalidate();}}
    /** Keep a compact footer symbol while its theme supplies the soft accent surface. */
    void inline(){inline=true;glyph=null;invalidate();}
    void symbol(String value){if(!value.equals(symbol)){symbol=value;glyph=null;invalidate();}}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);int nextSize=poster?Math.round(Math.min(getWidth(),getHeight())*(inline?.52f:.44f)):Math.round(26*getResources().getDisplayMetrics().density),nextTint=getCurrentTextColor();
        if(glyph==null||tint!=nextTint||size!=nextSize){tint=nextTint;size=nextSize;glyph=new PanelIcon(symbol,tint,size);}
        int x=(getWidth()-size)/2,y=(getHeight()-size)/2;glyph.setBounds(x,y,x+size,y+size);glyph.draw(canvas);
    }
}

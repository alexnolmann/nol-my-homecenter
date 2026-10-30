package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.widget.Button;

/** Retains native button semantics; poster icon, label and selection form one centred group. */
final class NavigationButton extends Button {
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG),line=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect ink=new Rect();
    private final int iconSize,gap,lineGap,lineHeight,lineWidth,sidePadding;
    private boolean poster;
    private PanelIcon icon;

    NavigationButton(Context context){super(context);PanelStyle s=new PanelStyle(context);iconSize=s.dp(28);gap=s.dp(4);lineGap=s.dp(6);lineHeight=s.dp(2);lineWidth=s.dp(36);sidePadding=s.dp(4);}
    void appearance(boolean poster,String name,int iconColor,int signal){this.poster=poster;icon=poster?new PanelIcon(name,iconColor,iconSize):null;line.setColor(signal);invalidate();}

    @Override protected void onDraw(Canvas canvas){
        if(!poster){super.onDraw(canvas);return;}
        String label=getText().toString();text.setTypeface(getTypeface());text.setTextSize(getTextSize());text.setTextScaleX(1);text.setColor(getCurrentTextColor());
        float width=text.measureText(label),available=Math.max(1,getWidth()-2*sidePadding);if(width>available)text.setTextScaleX(available/width);
        text.getTextBounds(label,0,label.length(),ink);
        float groupHeight=iconSize+gap+ink.height()+lineGap+lineHeight,top=(getHeight()-groupHeight)/2f;
        int left=(getWidth()-iconSize)/2,iconTop=Math.round(top);icon.setBounds(left,iconTop,left+iconSize,iconTop+iconSize);icon.draw(canvas);
        float labelTop=top+iconSize+gap,baseline=labelTop-ink.top;
        canvas.drawText(label,(getWidth()-text.measureText(label))/2f,baseline,text);
        if(isSelected()||isPressed()||isFocused()){
            float y=labelTop+ink.height()+lineGap,half=Math.min(lineWidth,available)/2f;
            canvas.drawRect(getWidth()/2f-half,y,getWidth()/2f+half,y+lineHeight,line);
        }
    }
}

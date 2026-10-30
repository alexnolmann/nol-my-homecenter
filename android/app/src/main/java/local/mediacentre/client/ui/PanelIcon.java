package local.mediacentre.client.ui;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Small local line icons drawn directly on API 19; no raster decoding or animation. */
final class PanelIcon extends Drawable {
    private final String kind;private final int tint,size;private final Paint p=new Paint(3);
    PanelIcon(String kind,int tint,int size){this.kind=kind;this.tint=tint;this.size=size;}
    private void line(Canvas c,float... xy){Path path=new Path();path.moveTo(xy[0],xy[1]);for(int n=2;n<xy.length;n+=2)path.lineTo(xy[n],xy[n+1]);c.drawPath(path,p);}
    @Override public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);p.setColor(tint);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(kind){
        case "search":canvas.drawCircle(10,10,7,p);line(canvas,15,15,22,22);break;
        case "home":line(canvas,3,11,12,3,21,11,21,21,15,21,15,14,9,14,9,21,3,21,3,11);break;
        case "weather":canvas.drawCircle(9,8,4,p);line(canvas,9,1,9,2);line(canvas,2,8,1,8);line(canvas,16,8,17,8);cloud(canvas);break;
        case "climate":canvas.drawRoundRect(new RectF(6,2,12,16),3,3,p);canvas.drawCircle(9,18,4,p);line(canvas,9,7,9,19);line(canvas,15,5,20,5);line(canvas,15,10,18,10);break;
        case "radio":canvas.drawRoundRect(new RectF(3,8,21,21),2,2,p);line(canvas,4,5,18,2);canvas.drawCircle(8,15,2.5f,p);line(canvas,14,12,18,12);line(canvas,14,16,18,16);break;
        case "stations":for(int y:new int[]{5,12,19}){canvas.drawCircle(3,y,0.7f,p);line(canvas,8,y,21,y);}break;
        case "folder_solid":p.setStyle(Paint.Style.FILL);line(canvas,2,7,2,4,9,4,12,7,22,7,22,20,2,20,2,7);break;
        case "previous_solid":p.setStyle(Paint.Style.FILL);canvas.drawRect(4,5,6,19,p);line(canvas,20,4,7,12,20,20,20,4);break;
        case "next_solid":p.setStyle(Paint.Style.FILL);canvas.drawRect(18,5,20,19,p);line(canvas,4,4,17,12,4,20,4,4);break;
        case "folder":line(canvas,2,7,2,4,9,4,12,7,22,7,22,20,2,20,2,7);break;
        case "up":line(canvas,5,12,12,5,19,12);line(canvas,12,5,12,21);break;
        case "chevron_right":line(canvas,9,5,16,12,9,19);break;
        case "more":p.setStyle(Paint.Style.FILL);for(int y:new int[]{5,12,19})canvas.drawCircle(12,y,1.5f,p);break;
        case "shuffle":line(canvas,3,5,7,5,17,19,22,19);line(canvas,3,19,7,19,17,5,22,5);line(canvas,19,2,22,5,19,8);line(canvas,19,16,22,19,19,22);break;
        case "chevron_down":line(canvas,6,9,12,15,18,9);break;
        case "bluetooth":line(canvas,6,6,17,17,12,22,12,2,17,7,6,18);break;
        case "speaker":line(canvas,3,9,7,9,12,4,12,20,7,15,3,15,3,9);canvas.drawArc(new RectF(7,6,19,18),-55,110,false,p);canvas.drawArc(new RectF(5,2,23,22),-45,90,false,p);break;
        case "speaker_muted":line(canvas,3,9,7,9,12,4,12,20,7,15,3,15,3,9);line(canvas,16,8,23,16);line(canvas,23,8,16,16);break;
        case "music":line(canvas,10,17,10,4,20,2,20,15);canvas.drawOval(new RectF(4,16,10,21),p);canvas.drawOval(new RectF(14,14,20,19),p);break;
        case "pause":p.setStyle(Paint.Style.FILL);canvas.drawRoundRect(new RectF(6,5,10,19),1,1,p);canvas.drawRoundRect(new RectF(14,5,18,19),1,1,p);break;
        case "previous":line(canvas,5,5,5,19);line(canvas,19,5,8,12,19,19,19,5);break;
        case "next":line(canvas,19,5,19,19);line(canvas,5,5,16,12,5,19,5,5);break;
        case "repeat":case "repeat_one":
            p.setStrokeWidth(1.9f);Path loop=new Path();loop.moveTo(4,11);loop.lineTo(4,8);loop.quadTo(4,5,7,5);loop.lineTo(20,5);loop.moveTo(20,13);loop.lineTo(20,16);loop.quadTo(20,19,17,19);loop.lineTo(4,19);canvas.drawPath(loop,p);
            line(canvas,17,2,20,5,17,8);line(canvas,7,16,4,19,7,22);
            if(kind.equals("repeat_one")){p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(9);p.setTextAlign(Paint.Align.CENTER);canvas.drawText("1",12,15,p);}break;
        case "loading":canvas.drawArc(new RectF(4,4,20,20),-70,270,false,p);break;
        case "finance":line(canvas,3,21,3,13);line(canvas,9,21,9,8);line(canvas,15,21,15,12);line(canvas,21,21,21,3);break;
        case "settings":canvas.drawCircle(12,12,6,p);canvas.drawCircle(12,12,2,p);for(int n=0;n<8;n++){canvas.save();canvas.rotate(n*45,12,12);line(canvas,12,2,12,5);canvas.restore();}break;
        case "photoframe":canvas.drawRoundRect(new RectF(2,4,22,20),2,2,p);canvas.drawCircle(8,9,1.5f,p);line(canvas,3,17,9,12,13,16,16,13,21,18);break;
        case "check":line(canvas,5,12,10,17,20,6);break;
        case "play":p.setStyle(Paint.Style.FILL);line(canvas,8,4,20,12,8,20,8,4);break;
        case "stop":p.setStyle(Paint.Style.FILL);canvas.drawRoundRect(new RectF(6,6,18,18),2,2,p);break;
        default:cloud(canvas);
        }canvas.restore();}
    private void cloud(Canvas c){Path path=new Path();path.moveTo(6,20);path.cubicTo(0,20,1,12,7,12);path.cubicTo(8,5,18,6,18,13);path.cubicTo(24,13,24,20,18,20);path.close();c.drawPath(path,p);}
    @Override public void setAlpha(int alpha){p.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public ConstantState getConstantState(){return new ConstantState(){@Override public Drawable newDrawable(){return new PanelIcon(kind,tint,size);}@Override public int getChangingConfigurations(){return 0;}};}
}

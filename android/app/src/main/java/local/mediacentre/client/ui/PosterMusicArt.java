package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Honest fallback artwork for MP3s without embedded covers: static vinyl and album typography. */
final class PosterMusicArt extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path cut=new Path();
    private String artist="ДОМАШНЯЯ",album="МУЗЫКА";private final Typeface face;
    PosterMusicArt(Context c){super(c);face=PosterTypography.face(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void track(String name,String artist){String a=artist==null||artist.isEmpty()?"МУЗЫКА":artist,n=name==null||name.isEmpty()?"ВЫБЕРИТЕ ТРЕК":name;if(!a.equals(this.artist)||!n.equals(album)){this.artist=a;album=n;invalidate();}}
    protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();p.setStyle(Paint.Style.FILL);p.setColor(0xff0738e8);c.drawRect(0,0,w,h,p);int save=c.save();cut.rewind();cut.moveTo(0,0);cut.lineTo(w*.55f,0);cut.lineTo(w*.82f,h);cut.lineTo(0,h);cut.close();c.clipPath(cut);p.setColor(0xff071522);c.drawRect(0,0,w,h,p);float radius=h*.61f,cx=w*.30f,cy=h*.6f;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(h*.003f);for(int n=0;n<19;n++){p.setColor(0xff293b4b);c.drawCircle(cx,cy,radius*(.30f+n*.035f),p);}p.setStyle(Paint.Style.FILL);p.setColor(0xffe64a0b);c.drawCircle(cx,cy,radius*.25f,p);p.setColor(0xfff8f5ee);c.drawCircle(cx,cy,radius*.025f,p);c.restoreToCount(save);p.setColor(Color.WHITE);p.setTypeface(face);p.setTextSize(h*.105f);p.setTextScaleX(.8f);String text=artist.toUpperCase(java.util.Locale.ROOT);float x=w*.67f,y=h*.18f;String[] words=text.split(" ");for(int n=0;n<Math.min(4,words.length);n++){String word=words[n];while(word.length()>1&&p.measureText(word)>w*.30f)word=word.substring(0,word.length()-1);c.drawText(word,x,y+n*h*.13f,p);}p.setTextSize(h*.052f);p.setColor(0xffb1caff);String label=album;while(label.length()>1&&p.measureText(label)>w*.29f)label=label.substring(0,label.length()-1);c.drawText(label,x,h*.88f,p);
    }
}

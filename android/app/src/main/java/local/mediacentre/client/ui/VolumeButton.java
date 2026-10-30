package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.Button;
import local.mediacentre.client.R;
import local.mediacentre.client.radio.RadioStore;

/** A full-size touch target around a speaker icon; no timer of its own. */
final class VolumeButton extends Button {
    private boolean muted,initialized;
    private PanelIcon glyph;
    private int size,tint;
    VolumeButton(Context c,Runnable action){
        this(c,R.id.mini_player_mute,action);
    }
    VolumeButton(Context c,int id,Runnable action){
        super(c);setId(id);setText("");setPadding(0,0,0,0);
        setOnClickListener(v->{action.run();refresh();});refresh();
    }
    void refresh(){
        boolean next=RadioStore.volume(getContext())==0;
        if(!initialized||next!=muted){initialized=true;muted=next;glyph=null;setContentDescription(muted?"Включить звук":"Выключить звук");invalidate();}
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        int nextSize=Math.round(Math.min(getWidth(),getHeight())*.525f),nextTint=getCurrentTextColor();
        if(glyph==null||size!=nextSize||tint!=nextTint){size=nextSize;tint=nextTint;glyph=new PanelIcon(muted?"speaker_muted":"speaker",tint,size);}
        int x=(getWidth()-size)/2,y=(getHeight()-size)/2;glyph.setBounds(x,y,x+size,y+size);glyph.draw(c);
    }
}

package local.mediacentre.client.ui;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.*;
import local.mediacentre.client.radio.AudioOutputController;

/** The output label and chevron open the same anchored output menu. */
public final class AudioOutputControls extends LinearLayout {
    private final Button toggle,choose;
    private final AudioOutputController output;
    private boolean started,reversed;
    private PopupWindow menu;
    private String drawnIcon="";private Boolean drawnDark,drawnPoster;
    private final Runnable changed=this::refresh;
    public AudioOutputControls(Context c){this(c,null);}
    public AudioOutputControls(Context c,AttributeSet attrs){
        super(c,attrs);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
        PanelStyle s=new PanelStyle(c);output=AudioOutputController.get(c);
        toggle=s.button("Динамики","audio_output_toggle",this::openMenu);
        toggle.setSingleLine();toggle.setEllipsize(android.text.TextUtils.TruncateAt.END);toggle.setTextSize(16);toggle.setCompoundDrawablePadding(s.dp(8));
        toggle.setMaxWidth(s.dp(208));setMinimumWidth(s.dp(240));addView(toggle,new LayoutParams(0,s.dp(48),1));
        choose=new MusicTransportButton(c,"chevron_down");s.buttonStyle(choose,false);choose.setContentDescription("audio_output_choose");choose.setOnClickListener(v->openMenu());
        choose.setTextSize(24);addView(choose,new LayoutParams(s.dp(40),s.dp(48)));setOnClickListener(v->openMenu());

    }
    private void openMenu(){if(output.busy())return;if(menu!=null&&menu.isShowing()){menu.dismiss();return;}menu=AudioOutputUi.dropdown((Activity)getContext(),this);}
    private void closeMenu(){if(menu!=null){menu.dismiss();menu=null;}}
    void contrast(boolean value){if(reversed!=value){reversed=value;drawnIcon="";refresh();}}
    public void start(){if(!started){started=true;output.listen(changed);}}
    public void stop(){closeMenu();if(started){started=false;output.unlisten(changed);}}
    @Override protected void onDetachedFromWindow(){closeMenu();super.onDetachedFromWindow();}
    public void refresh(){
        if(!started)return;android.view.View current=getRootView().findViewWithTag("audio_output_current");if(current instanceof TextView)PanelStyle.update((TextView)current,output.label());PanelStyle.update(toggle,output.label());toggle.setEnabled(!output.busy());choose.setEnabled(!output.busy());
        PanelStyle s=new PanelStyle(getContext());
        String icon=output.busy()?"loading":output.routedBluetooth()?"bluetooth":"speaker";
        if(!icon.equals(drawnIcon)||drawnDark==null||drawnDark!=s.palette.dark||drawnPoster==null||drawnPoster!=s.poster){drawnIcon=icon;drawnDark=s.palette.dark;drawnPoster=s.poster;s.repaint(toggle);s.repaint(choose);if(s.poster){s.bind(toggle,p->{toggle.setTextColor(reversed?android.graphics.Color.WHITE:p.color(PanelStyle.TEXT));toggle.setBackgroundColor(android.graphics.Color.TRANSPARENT);});s.bind(choose,p->{choose.setTextColor(reversed?android.graphics.Color.WHITE:p.color(PanelStyle.TEXT));choose.setBackgroundColor(android.graphics.Color.TRANSPARENT);});}else{s.buttonStyle(toggle,false);s.buttonStyle(choose,false);}toggle.setTypeface(s.poster?PosterTypography.face(getContext()):android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));((MusicTransportButton)choose).design(s.poster);toggle.setCompoundDrawablesWithIntrinsicBounds(new PanelIcon(icon,reversed?android.graphics.Color.WHITE:s.color(PanelStyle.ACCENT),s.dp(22)),null,null,null);}
    }
}

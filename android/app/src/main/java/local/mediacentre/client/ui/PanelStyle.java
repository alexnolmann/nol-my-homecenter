package local.mediacentre.client.ui;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import local.mediacentre.client.R;
import local.mediacentre.client.presentation.ThemePalette;
import local.mediacentre.client.presentation.DesignStyle;

final class PanelStyle {
    static final int BG=ThemePalette.BG,CARD=ThemePalette.CARD,TEXT=ThemePalette.TEXT,MUTED=ThemePalette.MUTED,ACCENT=ThemePalette.ACCENT,WARNING=ThemePalette.WARNING,DOWN=ThemePalette.DOWN,LINE=ThemePalette.LINE,SOFT=ThemePalette.SOFT,NAV=ThemePalette.NAV,ON_ACCENT=ThemePalette.ON_ACCENT,PRESSED=ThemePalette.PRESSED,DISABLED=ThemePalette.DISABLED,SIGNAL=ThemePalette.SIGNAL,NAV_TEXT=ThemePalette.NAV_TEXT,NAV_MUTED=ThemePalette.NAV_MUTED,ON_SIGNAL=ThemePalette.ON_SIGNAL;
    interface Binding{void apply(PanelStyle s);}
    final Context context;
    final ThemePalette palette;
    final boolean poster;
    PanelStyle(Context c){this(c,isDark(c));}
    PanelStyle(Context c,boolean dark){context=c;palette=new ThemePalette(dark,DisplaySettings.preferences(c).getString("design",DesignStyle.CLASSIC));poster=palette.poster;}
    private static boolean isDark(Context c){SharedPreferences p=DisplaySettings.preferences(c);Calendar now=Calendar.getInstance(TimeZone.getTimeZone(p.getString("timezone","Europe/Moscow")));return ThemePalette.dark(p.getString("theme","auto"),now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE),DisplaySettings.hour(p,"night_start",22),DisplaySettings.hour(p,"night_end",7));}
    int color(int role){return palette.color(role);}
    int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    void bind(View view,Binding binding){view.setTag(R.id.theme_binding,binding);binding.apply(this);}
    void repaint(View view){Object binding=view.getTag(R.id.theme_binding);if(binding instanceof Binding)((Binding)binding).apply(this);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++)repaint(group.getChildAt(n));}}
    GradientDrawable surface(int role){return surface(role,SOFT,14);}
    GradientDrawable surface(int role,int border,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color(role));d.setCornerRadius(dp(poster&&radius<80?0:radius));if(border>=0)d.setStroke(dp(1),color(border));return d;}
    void background(View view,int role){bind(view,s->view.setBackground(s.surface(role)));}
    void flat(View view,int role){bind(view,s->view.setBackgroundColor(s.color(role)));}
    void ink(TextView view,int role){bind(view,s->view.setTextColor(s.color(role)));}
    void status(TextView view){bind(view,s->{String value=view.getText().toString().toLowerCase(Locale.ROOT);boolean warning=value.contains("устар")||value.contains("нет ")||value.contains("недоступ")||value.contains("не удалось")||value.contains("частич")||value.contains("прежние")||value.contains("только на планшете");view.setTextColor(s.color(warning?WARNING:MUTED));});}
    String freshness(local.mediacentre.client.data.Dtos.Meta meta){return meta.offline?"Нет связи · сохранено":meta.expired?"УСТАРЕЛО":local.mediacentre.client.presentation.Presenters.freshness(meta.freshness);}
    TextView text(String value,int size,int role){TextView v=new TextView(context);v.setText(value);v.setTextSize(size);v.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));v.setIncludeFontPadding(false);v.setPadding(0,dp(2),0,dp(2));ink(v,role);return v;}
    TextView title(String value,int size){TextView v=text(value,size,TEXT);if(poster)PosterTypography.headline(v,size);else v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    TextView change(local.mediacentre.client.data.Dtos.Instrument item,int size){
        boolean comparable=local.mediacentre.client.presentation.FinancialLabels.comparable(item);
        TextView text=title(comparable?local.mediacentre.client.presentation.FinancialLabels.change(item).replace("↓","").replace("↑","").trim():"—",size);text.setCompoundDrawablePadding(dp(6));
        bind(text,s->{int color=comparable?(item.change.signum()<0?s.color(DOWN):s.palette.dark?0xff6edda0:0xff087c3c):s.color(MUTED);text.setTextColor(color);text.setCompoundDrawablesWithIntrinsicBounds(comparable?new PosterDelta(item.change.signum()<0,color,s.dp(Math.min(18,size*2/3))):null,null,null,null);});return text;
    }
    LinearLayout column(){LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.VERTICAL);return l;}
    SeekBar slider(){SeekBar v=new CenteredSeekBar(context);bind(v,s->{v.getProgressDrawable().setColorFilter(s.color(ACCENT),PorterDuff.Mode.SRC_IN);v.getThumb().setColorFilter(s.color(ACCENT),PorterDuff.Mode.SRC_IN);});return v;}
    LinearLayout card(){LinearLayout c=column();c.setPadding(dp(20),dp(16),dp(20),dp(16));if(poster)bind(c,s->c.setBackground(new PosterRule(s.color(CARD),s.color(LINE),s.dp(1))));else background(c,CARD);return c;}
    private StateListDrawable states(boolean primary){return states(primary,12);}
    private StateListDrawable states(boolean primary,int radius){StateListDrawable d=new StateListDrawable();d.addState(new int[]{-android.R.attr.state_enabled},surface(BG,LINE,radius));d.addState(new int[]{android.R.attr.state_pressed},surface(primary?PRESSED:SOFT,ACCENT,radius));d.addState(new int[]{android.R.attr.state_focused},surface(SOFT,ACCENT,radius));d.addState(new int[]{android.R.attr.state_selected},surface(SOFT,ACCENT,radius));d.addState(new int[]{},surface(primary?ACCENT:CARD,primary?ACCENT:LINE,radius));return d;}
    void plain(Button b,boolean light){bind(b,s->{b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{s.color(DISABLED),light?Color.WHITE:s.color(TEXT)}));StateListDrawable bg=new StateListDrawable();bg.addState(new int[]{android.R.attr.state_pressed},new ColorDrawable(light?0x35ffffff:s.color(SOFT)));bg.addState(new int[]{android.R.attr.state_focused},new ColorDrawable(light?0x35ffffff:s.color(SOFT)));bg.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));b.setBackground(bg);});}
    void buttonStyle(Button b,boolean primary){buttonStyle(b,primary,12);}
    void transportStyle(Button b,boolean primary){if(!poster||!primary){buttonStyle(b,primary,100);return;}bind(b,s->{b.setTextColor(s.color(b.isEnabled()?ON_SIGNAL:DISABLED));StateListDrawable states=new StateListDrawable();states.addState(new int[]{-android.R.attr.state_enabled},s.surface(BG,LINE,100));states.addState(new int[]{android.R.attr.state_pressed},s.surface(SOFT,SIGNAL,100));states.addState(new int[]{},s.surface(SIGNAL,SIGNAL,100));b.setBackground(states);});}
    void miniTransportStyle(Button b){bind(b,s->{
        int accent=s.color(SIGNAL);
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{s.color(DISABLED),accent}));
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},s.miniTransportSurface(s.color(DISABLED),0x0c));
        states.addState(new int[]{android.R.attr.state_pressed},s.miniTransportSurface(accent,0x40));
        states.addState(new int[]{android.R.attr.state_focused},s.miniTransportSurface(accent,0x30));
        states.addState(new int[]{},s.miniTransportSurface(accent,s.palette.dark?0x24:0x18));
        b.setBackground(states);
    });}
    private Drawable miniTransportSurface(int tint,int alpha){
        GradientDrawable face=new GradientDrawable();face.setColor((tint&0x00ffffff)|(alpha<<24));
        face.setCornerRadius(dp(12));face.setStroke(dp(1),(tint&0x00ffffff)|0x60000000);
        return new InsetDrawable(face,dp(8),dp(12),dp(8),dp(12));
    }
    private void buttonStyle(Button b,boolean primary,int radius){bind(b,s->{b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_focused},new int[]{}},new int[]{s.color(DISABLED),s.color(TEXT),s.color(primary?ON_ACCENT:TEXT)}));b.setBackground(s.states(primary,radius));});}
    Button button(String label,String description,Runnable action){Button b=new Button(context);b.setText(label);b.setTextSize(18);b.setTypeface(poster?PosterTypography.face(context):Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setAllCaps(false);b.setMinHeight(dp(52));b.setMinimumWidth(dp(52));b.setPadding(dp(12),0,dp(12),0);b.setGravity(Gravity.CENTER);buttonStyle(b,false);b.setContentDescription(description);b.setOnClickListener(v->action.run());return b;}
    Button primary(String label,String description,Runnable action){Button b=button(label,description,action);buttonStyle(b,true);return b;}
    Button navigationButton(String label,String icon,Runnable action){Button b=new NavigationButton(context);b.setText(label);b.setAllCaps(false);b.setMinHeight(dp(52));b.setMinimumWidth(dp(52));b.setContentDescription("nav_"+icon);b.setOnClickListener(v->action.run());navigation(b,icon);return b;}
    void navigation(Button b,String icon){bind(b,s->{
        b.setGravity(s.poster?Gravity.CENTER:Gravity.LEFT|Gravity.CENTER_VERTICAL);b.setTextSize(s.poster?12:18);b.setPadding(s.dp(s.poster?2:12),0,s.dp(s.poster?2:12),0);b.setCompoundDrawablePadding(s.dp(s.poster?4:10));
        b.setTypeface(Typeface.create(s.poster?"sans-serif":"sans-serif-medium",Typeface.NORMAL));
        if(s.poster){int ink=s.color(b.isSelected()?SIGNAL:NAV_TEXT);b.setTextColor(s.color(NAV_TEXT));StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_pressed},new ColorDrawable(0xff203247));states.addState(new int[]{android.R.attr.state_focused},new ColorDrawable(0xff203247));states.addState(new int[]{},new ColorDrawable(s.color(NAV)));b.setBackground(states);b.setCompoundDrawablesWithIntrinsicBounds(null,b instanceof NavigationButton?null:new PanelIcon(icon,ink,s.dp(28)),null,null);}
        else{b.setTextColor(s.color(b.isSelected()?ACCENT:TEXT));StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_pressed},s.surface(SOFT,ACCENT,12));d.addState(new int[]{android.R.attr.state_focused},s.surface(SOFT,ACCENT,12));d.addState(new int[]{android.R.attr.state_selected},s.surface(SOFT,ACCENT,12));d.addState(new int[]{},s.surface(NAV,-1,12));b.setBackground(d);b.setCompoundDrawablesWithIntrinsicBounds(new PanelIcon(icon,s.color(b.isSelected()?ACCENT:MUTED),s.dp(22)),null,null,null);}
        if(b instanceof NavigationButton)((NavigationButton)b).appearance(s.poster,icon,s.color(b.isSelected()?SIGNAL:NAV_TEXT),s.color(SIGNAL));
    });}
    TextView badge(String name,int size){TextView v=text(initials(name),size/3,ACCENT);v.setGravity(Gravity.CENTER);v.setTypeface(null,Typeface.BOLD);bind(v,s->{v.setTextColor(s.color(ACCENT));v.setBackground(s.surface(SOFT,-1,size/2));});return v;}
    static String initials(String name){if(name==null)return "FM";String clean=name.replaceAll("[^\\p{L}\\p{N}]+"," ").trim();if(clean.isEmpty())return "FM";String[] words=clean.split(" +");String first=new String(Character.toChars(words[0].codePointAt(0)));String second=words.length>1?new String(Character.toChars(words[1].codePointAt(0))):"";return (first+second).toUpperCase(Locale.ROOT);}
    android.app.AlertDialog.Builder dialog(){return new android.app.AlertDialog.Builder(context,palette.dark?android.app.AlertDialog.THEME_HOLO_DARK:android.app.AlertDialog.THEME_HOLO_LIGHT);}
    ImageView weather(String condition,int size){return weather(condition,size,false);}
    ImageView weather(String condition,int size,boolean blue){ImageView v=new ImageView(context);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);bind(v,s->v.setImageDrawable(s.poster?new PosterWeatherIcon(condition,s.dp(size),blue):new WeatherIcon(condition,s.palette.dark,s.dp(size))));return v;}
    void input(EditText v){v.setTextSize(20);v.setPadding(dp(16),dp(8),dp(16),dp(8));v.setMinHeight(dp(56));bind(v,s->{v.setTextColor(s.color(TEXT));v.setHintTextColor(s.color(MUTED));v.setBackground(s.states(false));});}
    void checkbox(CheckBox v){v.setTextSize(18);bind(v,s->{v.setTextColor(s.color(TEXT));if(v instanceof PosterSwitch){GradientDrawable blank=new GradientDrawable();blank.setColor(Color.TRANSPARENT);blank.setSize(0,0);v.setButtonDrawable(blank);((PosterSwitch)v).colors(s.color(ACCENT),s.color(LINE),0xffffffff);}else v.setButtonDrawable(new PanelCheck(s.dp(24),s.dp(12),s.color(ACCENT),s.color(CARD),s.color(LINE),s.color(ON_ACCENT)));v.setMinHeight(s.dp(52));});} // KitKat resets minHeight in setButtonDrawable: restore the touch target after every recolour.
    static void update(TextView v,String text){if(!android.text.TextUtils.equals(v.getText(),text))v.setText(text);}
    void gap(LinearLayout parent,int height){View v=new View(context);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    void columns(LinearLayout row){bind(row,s->{GradientDrawable line=new GradientDrawable();line.setColor(s.color(LINE));line.setSize(s.dp(1),s.dp(1));row.setDividerDrawable(line);row.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);row.setDividerPadding(s.dp(8));});}
    void rule(LinearLayout parent){View line=new View(context);flat(line,LINE);parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
    static View find(View root,String description){if(description.equals(root.getContentDescription()))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int n=0;n<group.getChildCount();n++){View found=find(group.getChildAt(n),description);if(found!=null)return found;}}return null;}
}

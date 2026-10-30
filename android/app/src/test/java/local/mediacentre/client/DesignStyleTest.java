package local.mediacentre.client;
import org.junit.Test;
import static org.junit.Assert.*;
import local.mediacentre.client.presentation.*;

public class DesignStyleTest {
    @Test public void existingAndUnknownPreferencesKeepClassic(){for(String value:new String[]{null,"","classic","future"})assertEquals(DesignStyle.CLASSIC,DesignStyle.normalize(value));assertTrue(DesignStyle.poster("neo_poster"));}
    @Test public void designAndNightModeAreIndependent(){for(boolean night:new boolean[]{false,true}){ThemePalette classic=new ThemePalette(night),poster=new ThemePalette(night,DesignStyle.NEO_POSTER);assertEquals(night,poster.dark);assertTrue(poster.poster);assertFalse(classic.poster);assertNotEquals(classic.color(ThemePalette.ACCENT),poster.color(ThemePalette.ACCENT));assertEquals(new ThemePalette(night,DesignStyle.CLASSIC).color(ThemePalette.BG),classic.color(ThemePalette.BG));}}
    private double luminance(int c){double sum=0;double[] weights={0.2126,0.7152,0.0722};for(int n=0;n<3;n++){double x=((c>>(16-8*n))&255)/255d;sum+=weights[n]*(x<=0.04045?x/12.92:Math.pow((x+0.055)/1.055,2.4));}return sum;}
    private double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);}
    @Test public void posterTextAndNavigationRemainLegible(){for(boolean night:new boolean[]{false,true}){ThemePalette p=new ThemePalette(night,DesignStyle.NEO_POSTER);for(int bg:new int[]{ThemePalette.BG,ThemePalette.CARD,ThemePalette.SOFT})for(int ink:new int[]{ThemePalette.TEXT,ThemePalette.MUTED,ThemePalette.ACCENT,ThemePalette.WARNING,ThemePalette.DOWN})assertTrue("Text contrast "+night+"/"+bg+"/"+ink,contrast(p.color(bg),p.color(ink))>=4.5);for(int ink:new int[]{ThemePalette.NAV_TEXT,ThemePalette.NAV_MUTED,ThemePalette.SIGNAL})assertTrue("Rail contrast",contrast(p.color(ThemePalette.NAV),p.color(ink))>=4.5);assertTrue("Large transport glyph contrast (WCAG non-text)",contrast(p.color(ThemePalette.SIGNAL),p.color(ThemePalette.ON_SIGNAL))>=3);}}
}

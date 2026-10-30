package local.mediacentre.client;
import org.junit.Test;
import static org.junit.Assert.*;
import local.mediacentre.client.presentation.ThemePalette;
public class ThemePaletteTest {
    private double channel(int n){double x=n/255d;return x<=0.04045?x/12.92:Math.pow((x+0.055)/1.055,2.4);}
    private double luminance(int c){return 0.2126*channel((c>>16)&255)+0.7152*channel((c>>8)&255)+0.0722*channel(c&255);}
    private double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);}
    @Test public void textAndControlContrastInBothThemes(){for(boolean dark:new boolean[]{false,true}){ThemePalette p=new ThemePalette(dark);for(int bg:new int[]{ThemePalette.BG,ThemePalette.CARD,ThemePalette.SOFT,ThemePalette.NAV})for(int ink:new int[]{ThemePalette.TEXT,ThemePalette.MUTED,ThemePalette.ACCENT,ThemePalette.WARNING,ThemePalette.DOWN})assertTrue("contrast "+dark+" / "+bg+" / "+ink,contrast(p.color(bg),p.color(ink))>=4.5);assertTrue(contrast(p.color(ThemePalette.ACCENT),p.color(ThemePalette.ON_ACCENT))>=4.5);for(int bg:new int[]{ThemePalette.CARD,ThemePalette.BG})assertTrue(contrast(p.color(ThemePalette.LINE),p.color(bg))>=3);}}
    @Test public void scheduleWrapsMidnightAndManualChoiceWins(){assertTrue(ThemePalette.dark("auto",23*60,22,7));assertTrue(ThemePalette.dark("auto",6*60+59,22,7));assertFalse(ThemePalette.dark("auto",7*60,22,7));assertFalse(ThemePalette.dark("light",23*60,22,7));assertTrue(ThemePalette.dark("dark",12*60,22,7));assertFalse(ThemePalette.dark("auto",12*60,7,7));}
}

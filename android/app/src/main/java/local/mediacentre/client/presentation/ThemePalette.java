package local.mediacentre.client.presentation;
/** Semantic colours shared by every screen; no Android dependency. */
public final class ThemePalette {
    public static final int BG=0,CARD=1,TEXT=2,MUTED=3,ACCENT=4,WARNING=5,DOWN=6,LINE=7,SOFT=8,NAV=9,ON_ACCENT=10,PRESSED=11,DISABLED=12,SIGNAL=13,NAV_TEXT=14,NAV_MUTED=15,ON_SIGNAL=16;
    public final boolean dark;
    public final boolean poster;
    private final int[] colors;
    public ThemePalette(boolean dark){this(dark,DesignStyle.CLASSIC);}
    public ThemePalette(boolean dark,String design){
        this.dark=dark;poster=DesignStyle.poster(design);
        if(poster)colors=dark
            ?new int[]{0xff0b101b,0xff141d2d,0xfff8f5ee,0xffc0cad9,0xff9ab7ff,0xffffb19a,0xffff9e8c,0xff63738b,0xff202d43,0xff050b14,0xff081326,0xffb8cbff,0xff8795aa,0xffff8a50,0xfff8f5ee,0xffbac8d7,0xff0b101b}
            :new int[]{0xfff8f5ee,0xfff8f5ee,0xff071019,0xff495464,0xff0738e8,0xff9c3400,0xffb9320c,0xff7f8792,0xffe4eafa,0xff061521,0xffffffff,0xff0024ae,0xff697584,0xffe64a0b,0xfff8f5ee,0xffbac8d7,0xffffffff};
        else colors=dark
            ?new int[]{0xff111923,0xff1b2735,0xffedf3fc,0xffb0c1d7,0xff93beff,0xffffd38a,0xffffaac0,0xff607994,0xff283d59,0xff15202d,0xff102440,0xffb4d1ff,0xff8898ae,0xff93beff,0xffedf3fc,0xffb0c1d7,0xff102440}
            :new int[]{0xfff2f6fc,0xffffffff,0xff172e4d,0xff4d627d,0xff225ebc,0xff8b5000,0xffa92544,0xff7890ac,0xffe5eefc,0xffeaf1fb,0xffffffff,0xff184a96,0xff62748c,0xff225ebc,0xff172e4d,0xff4d627d,0xffffffff};
    }
    public int color(int role){return colors[role];}
    public static boolean dark(String mode,int minute,int start,int end){return "dark".equals(mode)||!"light".equals(mode)&&DisplayPolicy.night(minute,start,end);}
}

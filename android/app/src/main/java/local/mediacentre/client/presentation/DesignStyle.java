package local.mediacentre.client.presentation;

/** Layout style is independent of the light/dark schedule. Unknown values keep the existing UI. */
public final class DesignStyle {
    public static final String CLASSIC="classic",NEO_POSTER="neo_poster";
    private DesignStyle(){}
    public static String normalize(String value){return NEO_POSTER.equals(value)?NEO_POSTER:CLASSIC;}
    public static boolean poster(String value){return NEO_POSTER.equals(normalize(value));}
}

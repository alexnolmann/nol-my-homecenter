package local.mediacentre.client.presentation;

/** Separate optional catalogue transport metadata from a station's display title. */
public final class PosterLabels {
    private PosterLabels(){}
    public static String station(String name){if(name==null)return "Станция не выбрана";int at=name.lastIndexOf(" · ");return at>0&&name.substring(at+3).matches("(?i)(MP3|AAC|OGG|OPUS)(\\s.*)?")?name.substring(0,at):name;}
    public static String quality(String name){if(name==null)return "";String title=station(name);return title.equals(name)?"":name.substring(title.length()+3);}
}

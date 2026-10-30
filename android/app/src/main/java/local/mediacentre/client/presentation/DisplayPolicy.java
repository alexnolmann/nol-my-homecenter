package local.mediacentre.client.presentation;
/** Minutes are local to the configured weather location; no source timestamp sets the clock. */
public final class DisplayPolicy {
    private DisplayPolicy(){}
    public static int percent(int value){return Math.max(5,Math.min(100,value));}
    public static boolean night(int minute,int startHour,int endHour){
        if(minute<0||minute>=1440||startHour<0||startHour>23||endHour<0||endHour>23||startHour==endHour)return false;
        int start=startHour*60,end=endHour*60;return start<end?minute>=start&&minute<end:minute>=start||minute<end;
    }
    public static int brightness(int day,int dim,boolean enabled,int minute,int start,int end){return enabled&&night(minute,start,end)?Math.min(percent(day),percent(dim)):percent(day);}
    public static boolean bootAllowed(boolean enabled,int api){return enabled&&api>=19&&api<29;}
}

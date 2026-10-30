package local.mediacentre.client.climate;

/** Avoid a reconnect storm after errors, resume, or repeated navigation. */
public final class ClimatePollPolicy {
    public static final long INTERVAL_MS=15*60_000L;
    private ClimatePollPolicy(){}
    public static boolean due(ClimateReading latest,Long attemptedAt,long elapsed,long now){
        return due(latest,attemptedAt,elapsed,now,INTERVAL_MS);
    }
    public static boolean due(ClimateReading latest,Long attemptedAt,long elapsed,long now,long interval){
        if(interval<60_000L||interval>120*60_000L)throw new IllegalArgumentException("Invalid poll interval");
        if(attemptedAt!=null)return elapsed<attemptedAt||elapsed-attemptedAt>=interval;
        return latest==null||now<latest.measuredAt||now-latest.measuredAt>=interval;
    }
}

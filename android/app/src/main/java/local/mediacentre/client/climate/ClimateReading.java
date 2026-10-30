package local.mediacentre.client.climate;

/** Stock LYWSD03MMC measurement, characteristic EBE0CCC1. No cloud or bind key. */
public final class ClimateReading {
    public final double temperature;
    public final int humidity, batteryMillivolts;
    public final long measuredAt;

    public ClimateReading(double temperature,int humidity,int batteryMillivolts,long measuredAt){
        if(Double.isNaN(temperature)||Double.isInfinite(temperature)||temperature<-50||temperature>85||humidity<0||humidity>100||batteryMillivolts<1500||batteryMillivolts>4000||measuredAt<=0)
            throw new IllegalArgumentException("Invalid sensor measurement");
        this.temperature=temperature;this.humidity=humidity;this.batteryMillivolts=batteryMillivolts;this.measuredAt=measuredAt;
    }
    public static ClimateReading decode(byte[] value,long now){
        if(value==null||value.length!=5)throw new IllegalArgumentException("Expected five measurement bytes");
        int temperature=(short)((value[0]&255)|((value[1]&255)<<8));
        int voltage=(value[3]&255)|((value[4]&255)<<8);
        return new ClimateReading(temperature/100.0,value[2]&255,voltage,now);
    }
    public boolean stale(long now){return now<measuredAt||now-measuredAt>15*60_000L;}
}

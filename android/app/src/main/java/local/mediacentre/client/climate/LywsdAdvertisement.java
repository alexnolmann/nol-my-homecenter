package local.mediacentre.client.climate;
import java.nio.charset.Charset;

/** Legacy BLE AD structures; accepts only this model, not arbitrary nearby devices. */
public final class LywsdAdvertisement {
    public static boolean matches(String name,byte[] data){
        if("LYWSD03MMC".equalsIgnoreCase(name))return true;
        if(data==null)return false;
        for(int offset=0;offset<data.length;){
            int length=data[offset]&255;if(length==0)break;
            if(length<1||offset+length>=data.length)break;
            int type=data[offset+1]&255,start=offset+2,count=length-1;
            if((type==8||type==9)&&"LYWSD03MMC".equalsIgnoreCase(new String(data,start,count,Charset.forName("UTF-8"))))return true;
            // Xiaomi MiBeacon service FE95, product ID 055B (LYWSD03MMC).
            if(type==0x16&&count>=6&&(data[start]&255)==0x95&&(data[start+1]&255)==0xfe&&(data[start+4]&255)==0x5b&&(data[start+5]&255)==0x05)return true;
            offset+=length+1;
        }
        return false;
    }
}

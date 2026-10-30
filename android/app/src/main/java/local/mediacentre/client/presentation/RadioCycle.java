package local.mediacentre.client.presentation;
import java.util.*;
import local.mediacentre.client.data.Dtos;
public final class RadioCycle {
    private RadioCycle(){}
    public static Dtos.Station next(List<Dtos.Station> stations,String selected,int direction){
        List<Dtos.Station> enabled=new ArrayList<>();for(Dtos.Station station:stations)if(station.enabled)enabled.add(station);
        if(enabled.isEmpty())return null;
        int current=-1;for(int n=0;n<enabled.size();n++)if(enabled.get(n).id.equals(selected)){current=n;break;}
        int at=current<0?(direction<0?enabled.size()-1:0):(current+(direction<0?-1:1)+enabled.size())%enabled.size();return enabled.get(at);
    }
}

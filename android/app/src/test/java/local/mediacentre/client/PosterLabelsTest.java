package local.mediacentre.client;
import org.junit.Test;
import static org.junit.Assert.*;
import local.mediacentre.client.presentation.PosterLabels;
public class PosterLabelsTest {
    @Test public void separatesOnlyExplicitTransportSuffix(){assertEquals("Radio Paradise",PosterLabels.station("Radio Paradise · MP3 128"));assertEquals("MP3 128",PosterLabels.quality("Radio Paradise · MP3 128"));assertEquals("Радио",PosterLabels.station("Радио · AAC"));}
    @Test public void preservesRealNamesAndMissingData(){for(String name:new String[]{"Радио · Санкт-Петербург","MP3 Radio","Radio · AAC Jazz · Live"}){assertEquals(name,PosterLabels.station(name));assertEquals("",PosterLabels.quality(name));}assertEquals("Станция не выбрана",PosterLabels.station(null));assertEquals("",PosterLabels.quality(null));}
}

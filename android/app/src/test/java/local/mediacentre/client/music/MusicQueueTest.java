package local.mediacentre.client.music;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class MusicQueueTest {
 @Test public void sequentialAndRepeat(){MusicQueue q=new MusicQueue();q.set(3,0);assertEquals(1,q.next(true));assertEquals(2,q.next(true));assertEquals(-1,q.next(true));q.repeat(MusicQueue.ALL);assertEquals(0,q.next(true));q.repeat(MusicQueue.ONE);assertEquals(0,q.next(true));assertEquals(1,q.next(false));assertEquals(0,q.previous());}
 @Test public void shuffleVisitsEachTrackOnce(){MusicQueue q=new MusicQueue(new Random(4));q.shuffle(true);q.set(20,7);q.repeat(MusicQueue.ALL);Set<Integer> seen=new HashSet<>();for(int i=0;i<20;i++){assertTrue(seen.add(q.current()));if(i<19)q.next(true);}int last=q.current();assertNotEquals(last,q.next(true));seen.clear();for(int i=0;i<20;i++){assertTrue(seen.add(q.current()));if(i<19)q.next(true);}int current=q.current();q.shuffle(false);assertEquals(current,q.current());q.shuffle(true);assertEquals(current,q.current());}
 @Test public void singleTrackAndBounds(){MusicQueue q=new MusicQueue();q.set(1,0);assertEquals(-1,q.next(true));q.repeat(MusicQueue.ONE);assertEquals(0,q.next(true));assertEquals(0,q.previous());try{q.set(5001,0);fail();}catch(IllegalArgumentException expected){}}
}

package local.mediacentre.client;
import local.mediacentre.client.radio.OutputSwitch;
import org.junit.Test;
import static org.junit.Assert.*;
public class OutputSwitchTest {
    @Test public void connectedDoesNotMeanRouted(){
        OutputSwitch s=new OutputSwitch();long id=s.begin(0,true);
        assertEquals(OutputSwitch.WAITING,s.poll(id,1000,true,false));
        assertEquals(OutputSwitch.WAITING,s.poll(id,9000,true,false));
        assertEquals(OutputSwitch.WAITING,s.poll(id,10000,true,true));
        assertEquals(OutputSwitch.READY_RESUME,s.poll(id,10750,true,true));
    }
    @Test public void routeMustRemainStable(){
        OutputSwitch s=new OutputSwitch();long id=s.begin(0,true);
        s.poll(id,100,true,true);s.poll(id,800,true,false);
        assertEquals(OutputSwitch.WAITING,s.poll(id,900,true,true));
        assertEquals(OutputSwitch.WAITING,s.poll(id,1600,true,true));
        assertEquals(OutputSwitch.READY_RESUME,s.poll(id,1650,true,true));
    }
    @Test public void stopDuringConnectionCancelsResumeButAllowsRoute(){
        OutputSwitch s=new OutputSwitch();long id=s.begin(0,true);s.cancelResume();
        s.poll(id,100,true,true);
        assertEquals(OutputSwitch.READY_IDLE,s.poll(id,850,true,true));assertFalse(s.busy());
    }
    @Test public void pausedSourceDoesNotStart(){
        OutputSwitch s=new OutputSwitch();long id=s.begin(0,false);
        s.poll(id,1,true,true);
        assertEquals(OutputSwitch.READY_IDLE,s.poll(id,751,true,true));
    }
    @Test public void deadlineNeverResumesEvenWithLateSuccess(){
        OutputSwitch s=new OutputSwitch();long id=s.begin(0,true);s.poll(id,19000,true,true);
        assertEquals(OutputSwitch.TIMED_OUT,s.poll(id,20000,true,true));
        assertEquals(OutputSwitch.STALE,s.poll(id,21000,true,true));
    }
    @Test public void callbacksFromReplacedRequestCannotResume(){
        OutputSwitch s=new OutputSwitch();long old=s.begin(0,true),current=s.begin(100,false);
        assertEquals(OutputSwitch.STALE,s.poll(old,1000,true,true));assertTrue(s.busy());
        s.poll(current,1000,true,true);
        assertEquals(OutputSwitch.READY_IDLE,s.poll(current,1750,true,true));
    }
    @Test public void cancellationInvalidatesSettledCallback(){
        OutputSwitch s=new OutputSwitch();long id=s.begin(0,true);s.poll(id,100,true,true);s.cancel();
        assertEquals(OutputSwitch.STALE,s.poll(id,900,true,true));assertFalse(s.busy());
    }
}

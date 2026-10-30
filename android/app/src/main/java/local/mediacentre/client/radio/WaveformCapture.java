package local.mediacentre.client.radio;

import java.util.Arrays;

/** Main-thread session ownership. No timer; capture exists only for a visible, permitted consumer. */
public final class WaveformCapture {
    public interface Source {int size();boolean read(byte[] data);void close();}
    public interface Factory {Source open(int session);}
    private final Factory factory;
    private Source source;
    private byte[] data;
    private int session,failedSession=-1;
    private boolean visible;
    public WaveformCapture(Factory factory){this.factory=factory;}
    public boolean capturing(){return source!=null;}
    public void target(int requested,boolean visible){
        int next=Math.max(0,requested);
        if(session!=next||this.visible!=visible){closeSource();failedSession=-1;}
        session=next;this.visible=visible;
        if(!visible||session==0||source!=null||failedSession==session)return;
        try{
            source=factory.open(session);
            int size=source.size();
            if(size<128||size>1024||(size&(size-1))!=0)throw new IllegalArgumentException("Invalid capture size");
            if(data==null||data.length!=size)data=new byte[size];
        }catch(RuntimeException failed){fail();}
    }
    public boolean read(float[] output){
        if(source==null){Arrays.fill(output,0);return false;}
        try{
            if(!source.read(data)){fail();Arrays.fill(output,0);return false;}
            for(int i=0;i<output.length;i++)output[i]=((data[i*data.length/output.length]&255)-128)/128f;
            return true;
        }catch(RuntimeException failed){fail();Arrays.fill(output,0);return false;}
    }
    public void close(){target(0,false);}
    private void fail(){closeSource();failedSession=session;}
    private void closeSource(){Source old=source;source=null;if(old!=null)try{old.close();}catch(RuntimeException ignored){}}
}

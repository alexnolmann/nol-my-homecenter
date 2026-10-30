package local.mediacentre.client.music;
import java.util.Random;
/** Immutable membership, stable shuffle cycle, manual Next ignores repeat-one. */
public final class MusicQueue {
    public static final int OFF=0,ALL=1,ONE=2;
    private final Random random;private int[] order=new int[0];private int cursor;
    private boolean shuffled;private int repeat;
    public MusicQueue(){this(new Random());}
    public MusicQueue(Random random){this.random=random;}
    public void set(int size,int selected){if(size<1||size>5000||selected<0||selected>=size)throw new IllegalArgumentException("Bounded queue required.");order=new int[size];for(int i=0;i<size;i++)order[i]=i;cursor=selected;if(shuffled)shuffleKeeping(selected);}
    public int current(){return order.length==0?-1:order[cursor];}
    public int size(){return order.length;}
    public boolean shuffled(){return shuffled;}
    public int repeat(){return repeat;}
    public void repeat(int mode){if(mode<0||mode>2)throw new IllegalArgumentException();repeat=mode;}
    public void shuffle(boolean enabled){if(shuffled==enabled)return;int selected=current();shuffled=enabled;if(order.length==0)return;if(enabled)shuffleKeeping(selected);else{for(int i=0;i<order.length;i++)order[i]=i;cursor=selected;}}
    private void shuffleKeeping(int selected){int n=1;order[0]=selected;for(int i=0;i<order.length;i++)if(i!=selected)order[n++]=i;for(int i=order.length-1;i>1;i--){int j=1+random.nextInt(i);int t=order[i];order[i]=order[j];order[j]=t;}cursor=0;}
    public int next(boolean completed){
        if(order.length==0)return -1;if(completed&&repeat==ONE)return current();
        if(cursor+1<order.length)return order[++cursor];
        if(repeat!=ALL)return -1;
        int old=current();if(shuffled){shuffleKeeping(old);if(order.length>1){int t=order[0];order[0]=order[1];order[1]=t;}}cursor=0;return current();
    }
    public int previous(){if(order.length==0)return -1;if(cursor>0)cursor--;else if(repeat==ALL)cursor=order.length-1;return current();}
}

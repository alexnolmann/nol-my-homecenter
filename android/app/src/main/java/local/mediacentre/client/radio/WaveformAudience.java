package local.mediacentre.client.radio;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Main-thread ownership: one hidden view cannot close capture still used by another. */
public final class WaveformAudience {
    private final WaveformCapture capture;
    private final Set<Object> viewers=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
    private int session;
    public WaveformAudience(WaveformCapture capture){this.capture=capture;}
    public void target(int session){this.session=session;sync();}
    public void visible(Object owner,boolean visible){if(visible)viewers.add(owner);else viewers.remove(owner);sync();}
    public boolean read(float[] output){return capture.read(output);}
    private void sync(){capture.target(session,!viewers.isEmpty());}
}

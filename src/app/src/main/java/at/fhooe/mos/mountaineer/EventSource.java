package at.fhooe.mos.mountaineer;

import java.util.ArrayList;

/**
 * Created by stefan on 25.11.2017.
 */

public abstract class EventSource<T> implements EventSourceInterface<T> {
    private ArrayList<T> eventListeners = new ArrayList<>();

    public void registerListener(T listener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    public void removeListener(T listener) {
        eventListeners.remove(listener);
    }

    protected ArrayList<T> getEventListeners() {
        return eventListeners;
    }
}

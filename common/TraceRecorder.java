package common;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe recorder of everything that happens during a run.
 *
 * Every synchronization program calls record(...) at the exact moment a
 * thread changes state (for example "reader R1 enters the critical section").
 * The recorded list is later turned into the HTML simulation by
 * SimulationHtmlWriter, so the animation is a replay of the REAL execution
 * and not a pre-scripted sequence.
 *
 * The method is synchronized, so the order of events in the list is the
 * order in which the threads really performed the actions. Callers must
 * therefore call record(...) while they still hold the lock/semaphore that
 * makes the action valid (see the comments in each program).
 */
public class TraceRecorder {

    /** One immutable state change. */
    public static final class Event {
        public final int seq;        // position in the trace (0, 1, 2, ...)
        public final long timeMs;    // milliseconds since the run started
        public final String actor;   // R1, W1, P0, Fork2, ...
        public final String state;   // READING, WAITING, EATING, TAKEN, ...
        public final String note;    // short human readable explanation
        public final int value;      // extra number (shared value / fork owner), -1 if unused

        Event(int seq, long timeMs, String actor, String state, String note, int value) {
            this.seq = seq;
            this.timeMs = timeMs;
            this.actor = actor;
            this.state = state;
            this.note = note;
            this.value = value;
        }
    }

    private final List<Event> events = new ArrayList<>();
    private final long startNanos = System.nanoTime();

    /** Records one state change and echoes it to the console. */
    public synchronized void record(String actor, String state, String note, int value) {
        long timeMs = (System.nanoTime() - startNanos) / 1_000_000L;
        Event e = new Event(events.size(), timeMs, actor, state, note, value);
        events.add(e);
        System.out.printf("[%5d ms] #%-3d %-6s -> %-15s %s%n", timeMs, e.seq, actor, state, note);
    }

    /** Convenience overload for events that carry no number. */
    public void record(String actor, String state, String note) {
        record(actor, state, note, -1);
    }

    /** Returns a copy of the trace recorded so far. */
    public synchronized List<Event> snapshot() {
        return new ArrayList<>(events);
    }
}

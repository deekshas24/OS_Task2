package dining_philosophers.monitor;

import common.SimulationHtmlWriter;
import common.SimulationHtmlWriter.Actor;
import common.TraceRecorder;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DINING PHILOSOPHERS (exactly 4 philosophers) - MONITOR BASED
 *
 * Layout: philosopher i sits between fork i (left) and fork (i+1) % 4 (right).
 * Shared resources: the 4 forks (only ever touched inside the monitor).
 * Critical section: the time a philosopher holds BOTH forks (eating).
 *
 * The monitor (class TableMonitor) uses 'synchronized' methods for mutual exclusion and
 * wait()/notifyAll() as the condition variable "a philosopher may be able to eat now".
 *
 * A philosopher picks up BOTH forks at once, and only when both are free. Because it never
 * holds one fork while waiting for the other, "hold and wait" is impossible, so deadlock
 * cannot occur.
 *
 * Starvation consideration:
 *   The textbook version (eat if both neighbours are not eating) can starve a philosopher
 *   whose two neighbours keep taking turns. Here every hungry philosopher gets a ticket number
 *   and may only start eating when no hungry NEIGHBOUR holds an older ticket. The oldest
 *   waiting philosopher can therefore never be overtaken, so everybody eventually eats.
 *
 * Run:  java dining_philosophers.monitor.DiningPhilosophersMonitor [meals]
 */
public class DiningPhilosophersMonitor {

    static final int N = 4;                 // exactly four philosophers
    static int meals = 3;
    static final int MAX_DELAY_MS = 500;

    enum State { THINKING, HUNGRY, EATING }

    static final TraceRecorder trace = new TraceRecorder();
    static final Random random = new Random();

    static final AtomicInteger violations = new AtomicInteger();
    static final AtomicInteger[] mealsEaten = new AtomicInteger[N];
    static final long[] longestWaitMs = new long[N];   // longest time each philosopher stayed hungry

    static void pause() throws InterruptedException {
        Thread.sleep(50 + random.nextInt(MAX_DELAY_MS));
    }

    /** The monitor that controls access to the forks. */
    static class TableMonitor {
        private final State[] state = new State[N];
        private final long[] ticket = new long[N];     // arrival order of hungry philosophers
        private final int[] forkOwner = new int[N];    // -1 = free
        private long nextTicket = 0;

        TableMonitor() {
            for (int i = 0; i < N; i++) {
                state[i] = State.THINKING;
                forkOwner[i] = -1;
            }
        }

        private int leftNeighbour(int i)  { return (i + N - 1) % N; }
        private int rightNeighbour(int i) { return (i + 1) % N; }

        /** True if philosopher i may take both forks now. Always called inside the monitor. */
        private boolean mayEat(int i) {
            int l = leftNeighbour(i), r = rightNeighbour(i);
            if (state[l] == State.EATING || state[r] == State.EATING) return false; // a fork is in use
            // do not overtake an older hungry neighbour (prevents starvation)
            if (state[l] == State.HUNGRY && ticket[l] < ticket[i]) return false;
                        return !(state[r] == State.HUNGRY && ticket[r] < ticket[i]);
        }

        /** Philosopher i becomes hungry and blocks until both forks can be taken. */
        public synchronized void pickUpForks(int i) throws InterruptedException {
            long hungrySince = System.nanoTime();
            state[i] = State.HUNGRY;
            ticket[i] = nextTicket++;
            trace.record("P" + i, "HUNGRY", "is hungry");
            while (!mayEat(i)) {
                wait();                                    // condition: a neighbour must finish first
            }
            trace.record("P" + i, "ACQUIRING_FORKS", "asks for forks " + i + " and " + rightNeighbour(i));
            takeFork(i, i);
            takeFork(rightNeighbour(i), i);
            state[i] = State.EATING;
            long waited = (System.nanoTime() - hungrySince) / 1_000_000L;
            longestWaitMs[i] = Math.max(longestWaitMs[i], waited);
            trace.record("P" + i, "EATING", "eats after waiting " + waited + " ms");
        }

        /** Philosopher i puts both forks down and wakes up the waiting philosophers. */
        public synchronized void putDownForks(int i) {
            trace.record("P" + i, "RELEASING_FORKS", "puts down forks " + i + " and " + rightNeighbour(i));
            putFork(i, i);
            putFork(rightNeighbour(i), i);
            state[i] = State.THINKING;
            notifyAll();                                   // neighbours may be able to eat now
        }

        private void takeFork(int f, int who) {
            if (forkOwner[f] != -1) violations.incrementAndGet();   // must never happen
            forkOwner[f] = who;
            trace.record("F" + f, "TAKEN", "held by P" + who, who);
        }

        private void putFork(int f, int who) {
            if (forkOwner[f] != who) violations.incrementAndGet();  // must never happen
            forkOwner[f] = -1;
            trace.record("F" + f, "FREE", "put down by P" + who, -1);
        }
    }

    static final TableMonitor table = new TableMonitor();

    static class Philosopher extends Thread {
        private final int id;

        Philosopher(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < meals; i++) {
                    trace.record("P" + id, "THINKING", "thinks");
                    pause();
                    table.pickUpForks(id);
                    pause();                               // eating = critical section (outside the monitor lock)
                    mealsEaten[id].incrementAndGet();
                    table.putDownForks(id);
                }
                trace.record("P" + id, "DONE", "finished all meals, longest wait " + longestWaitMs[id] + " ms");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) meals = Integer.parseInt(args[0]);

        System.out.println("=== Dining Philosophers (Monitor): " + N + " philosophers, " + meals + " meals each ===");

        List<Actor> actors = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            mealsEaten[i] = new AtomicInteger();
            actors.add(new Actor("P" + i, "philosopher", "THINKING"));
        }
        for (int i = 0; i < N; i++) {
            actors.add(new Actor("F" + i, "fork", "FREE"));
        }

        Philosopher[] ph = new Philosopher[N];
        for (int i = 0; i < N; i++) {
            ph[i] = new Philosopher(i);
            ph[i].start();
        }
        for (Philosopher p : ph) p.join();       // if this ever hangs, the program has deadlocked

        StringBuilder eaten = new StringBuilder();
        for (int i = 0; i < N; i++) eaten.append(i > 0 ? ", " : "").append("P").append(i).append("=").append(mealsEaten[i].get());
        System.out.println("=== Finished without deadlock. Meals eaten: " + eaten
                + "; fork-ownership violations = " + violations.get() + " ===");

        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("Solution", "Monitor: synchronized methods + wait()/notifyAll()");
        summary.put("Philosophers / meals each", N + " / " + meals);
        summary.put("Meals eaten", eaten.toString());
        summary.put("Fork-ownership violations", String.valueOf(violations.get()));
        summary.put("Deadlock handling", "both forks taken together (no hold-and-wait)");
        summary.put("Starvation handling", "ticket order among hungry neighbours");

        String out = Paths.get("generated_html", "dining_philosophers_monitor.html").toString();
        SimulationHtmlWriter.write(Paths.get(out), "DP",
                "Dining Philosophers (Monitor)",
                actors, trace.snapshot(), summary);
        System.out.println("HTML simulation written to: " + Paths.get(out).toAbsolutePath());
    }
}

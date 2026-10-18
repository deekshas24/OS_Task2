package dining_philosophers.semaphore;

import common.SimulationHtmlWriter;
import common.SimulationHtmlWriter.Actor;
import common.TraceRecorder;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DINING PHILOSOPHERS (exactly 4 philosophers) - SEMAPHORE BASED
 *
 * Layout: philosopher i sits between fork i (left) and fork (i+1) % 4 (right).
 * Shared resources: the 4 forks.
 * Critical section: the time a philosopher holds BOTH of its forks (eating).
 *
 * Semaphores and initial values:
 *   fork[0..3] (1 each) - one binary semaphore per fork; acquire = pick up, release = put down.
 *   room       (3)      - at most 3 of the 4 philosophers may try to pick up forks at the same time.
 *
 * Deadlock prevention:
 *   Deadlock needs all four philosophers to hold their left fork and wait for the right one
 *   (circular wait). Because 'room' lets only 3 philosophers compete, at least one of them
 *   can always get both forks, eat, and put them back, so the circle can never close.
 *
 * Starvation consideration:
 *   Both 'room' and the forks are FAIR semaphores (first-come first-served queues), so no
 *   philosopher can be overtaken forever while it waits.
 *
 * Run:  java dining_philosophers.semaphore.DiningPhilosophersSemaphore [meals]
 */
public class DiningPhilosophersSemaphore {

    static final int N = 4;                 // exactly four philosophers
    static int meals = 3;                   // how many times each philosopher eats
    static final int MAX_DELAY_MS = 500;

    static final Semaphore[] fork = new Semaphore[N];
    static final Semaphore room = new Semaphore(N - 1, true);

    static final TraceRecorder trace = new TraceRecorder();
    static final Random random = new Random();

    // self-check used only for testing: who holds each fork right now (-1 = nobody)
    static final AtomicInteger[] owner = new AtomicInteger[N];
    static final AtomicInteger violations = new AtomicInteger();
    static final AtomicInteger[] mealsEaten = new AtomicInteger[N];
    static final long[] longestWaitMs = new long[N];   // longest time each philosopher stayed hungry

    static void pause() throws InterruptedException {
        Thread.sleep(50 + random.nextInt(MAX_DELAY_MS));
    }

    static class Philosopher extends Thread {
        private final int id;
        private final String name;
        private final int left;
        private final int right;

        Philosopher(int id) {
            this.id = id;
            this.name = "P" + id;
            this.left = id;
            this.right = (id + 1) % N;
        }

        private void takeFork(int f) throws InterruptedException {
            fork[f].acquire();                                           // wait(fork)
            if (!owner[f].compareAndSet(-1, id)) violations.incrementAndGet(); // fork must be free
            trace.record("F" + f, "TAKEN", "held by " + name, id);
        }

        private void putFork(int f) {
            owner[f].set(-1);
            trace.record("F" + f, "FREE", "put down by " + name, -1);    // record BEFORE release
            fork[f].release();                                           // signal(fork)
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < meals; i++) {
                    trace.record(name, "THINKING", i == 0 ? "sits down and thinks" : "thinks");
                    pause();

                    long hungrySince = System.nanoTime();
                    trace.record(name, "HUNGRY", "is hungry, waiting for a seat at the table");
                    room.acquire();                                      // wait(room)

                    trace.record(name, "ACQUIRING_FORKS", "asks for forks " + left + " and " + right);
                    takeFork(left);
                    trace.record(name, "ACQUIRING_FORKS", "holds fork " + left + ", waiting for fork " + right);
                    pause();                                             // makes a circular wait likely without 'room'
                    takeFork(right);
                    long waited = (System.nanoTime() - hungrySince) / 1_000_000L;
                    longestWaitMs[id] = Math.max(longestWaitMs[id], waited);

                    // ----- critical section: eating with both forks -----
                    trace.record(name, "EATING", "eats meal " + (i + 1) + " after waiting " + waited + " ms");
                    if (owner[left].get() != id || owner[right].get() != id) violations.incrementAndGet();
                    pause();
                    mealsEaten[id].incrementAndGet();

                    trace.record(name, "RELEASING_FORKS", "puts down forks " + left + " and " + right);
                    putFork(left);
                    putFork(right);
                    room.release();                                      // signal(room)
                }
                trace.record(name, "DONE", "finished all meals, longest wait " + longestWaitMs[id] + " ms");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) meals = Integer.parseInt(args[0]);

        System.out.println("=== Dining Philosophers (Semaphores): " + N + " philosophers, " + meals + " meals each ===");

        List<Actor> actors = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            fork[i] = new Semaphore(1, true);
            owner[i] = new AtomicInteger(-1);
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
        summary.put("Solution", "Semaphores: 4 fork semaphores + room(3)");
        summary.put("Philosophers / meals each", N + " / " + meals);
        summary.put("Meals eaten", eaten.toString());
        summary.put("Fork-ownership violations", String.valueOf(violations.get()));
        summary.put("Deadlock handling", "room(3) breaks the circular wait");
        summary.put("Starvation handling", "fair (FIFO) semaphores");

        String out = Paths.get("generated_html", "dining_philosophers_semaphore.html").toString();
        SimulationHtmlWriter.write(Paths.get(out), "DP",
                "Dining Philosophers (Semaphore)",
                actors, trace.snapshot(), summary);
        System.out.println("HTML simulation written to: " + Paths.get(out).toAbsolutePath());
    }
}

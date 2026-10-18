package readers_writers.semaphore;

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
 * READERS-WRITERS PROBLEM - SEMAPHORE BASED
 * (first readers-writers problem: readers have priority)
 *
 * Shared resource : the integer sharedData (stands for a file / database record).
 * Critical section: the code that reads or writes sharedData.
 *
 * Rules enforced by the semaphores:
 *   - many readers may be inside the critical section together,
 *   - a writer must be completely alone (no readers, no other writer).
 *
 * Semaphores and their initial values:
 *   resource (1) - "write lock". Held by a writer while it writes, and held on
 *                  behalf of ALL readers by the first reader that enters, until
 *                  the last reader leaves.
 *   mutex    (1) - protects the counter readCount (a tiny critical section of its own).
 *
 * Run:  java readers_writers.semaphore.ReadersWritersSemaphore [readers] [writers] [rounds]
 */
public class ReadersWritersSemaphore {

    // ---- configuration (can be changed here or through command line arguments) ----
    static int numReaders = 4;
    static int numWriters = 2;
    static int rounds = 3;               // how many times each thread reads / writes
    static final int MAX_DELAY_MS = 500; // random delays only make the interleaving visible

    // ---- shared state ----
    static int sharedData = 0;           // THE SHARED RESOURCE
    static int readCount = 0;            // number of readers currently inside (guarded by mutex)

    static final Semaphore resource = new Semaphore(1);
    static final Semaphore mutex = new Semaphore(1);

    // ---- self-check used only for testing (not part of the algorithm) ----
    static final AtomicInteger readersInside = new AtomicInteger();
    static final AtomicInteger writersInside = new AtomicInteger();
    static final AtomicInteger violations = new AtomicInteger();

    static final TraceRecorder trace = new TraceRecorder();
    static final Random random = new Random();

    static void pause() throws InterruptedException {
        Thread.sleep(50 + random.nextInt(MAX_DELAY_MS));
    }

    static class Reader extends Thread {
        private final String name;

        Reader(int id) {
            this.name = "R" + id;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < rounds; i++) {
                    pause();                                        // doing other work
                    trace.record(name, "WAITING", "wants to read");

                    // ----- entry section -----
                    mutex.acquire();                                // wait(mutex)
                    readCount++;
                    if (readCount == 1) {
                        resource.acquire();                         // first reader locks writers out
                    }
                    mutex.release();                                // signal(mutex)

                    // ----- critical section (reading) -----
                    if (writersInside.get() != 0) violations.incrementAndGet(); // must never happen
                    readersInside.incrementAndGet();
                    int value = sharedData;
                    trace.record(name, "READING", "read value " + value, value);
                    pause();                                        // time spent reading
                    readersInside.decrementAndGet();

                    // ----- exit section -----
                    mutex.acquire();                                // wait(mutex)
                    readCount--;
                    trace.record(name, "IDLE", "finished reading (" + readCount + " reader(s) left inside)");
                    if (readCount == 0) {
                        resource.release();                         // last reader lets writers in
                    }
                    mutex.release();                                // signal(mutex)
                }
                trace.record(name, "DONE", "finished all rounds");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static class Writer extends Thread {
        private final String name;

        Writer(int id) {
            this.name = "W" + id;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < rounds; i++) {
                    pause();
                    trace.record(name, "WAITING", "wants to write");

                    resource.acquire();                             // wait(resource): exclusive access

                    // ----- critical section (writing) -----
                    if (writersInside.incrementAndGet() != 1 || readersInside.get() != 0) {
                        violations.incrementAndGet();               // must never happen
                    }
                    int old = sharedData;
                    trace.record(name, "WRITING", "read " + old + ", about to write " + (old + 1), old);
                    // Read-modify-write with a delay in the middle. Without mutual exclusion
                    // two writers would overwrite each other and updates would be lost.
                    pause();
                    sharedData = old + 1;
                    writersInside.decrementAndGet();
                    trace.record(name, "IDLE", "wrote value " + sharedData, sharedData);

                    resource.release();                             // signal(resource)
                }
                trace.record(name, "DONE", "finished all rounds");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) numReaders = Integer.parseInt(args[0]);
        if (args.length > 1) numWriters = Integer.parseInt(args[1]);
        if (args.length > 2) rounds = Integer.parseInt(args[2]);

        System.out.println("=== Readers-Writers (Semaphores): " + numReaders + " readers, "
                + numWriters + " writers, " + rounds + " rounds each ===");

        List<Actor> actors = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 1; i <= numReaders; i++) {
            actors.add(new Actor("R" + i, "reader", "IDLE"));
            threads.add(new Reader(i));
        }
        for (int i = 1; i <= numWriters; i++) {
            actors.add(new Actor("W" + i, "writer", "IDLE"));
            threads.add(new Writer(i));
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        int expected = numWriters * rounds;
        System.out.println("=== Finished. Final value = " + sharedData + " (expected " + expected
                + "), mutual-exclusion violations = " + violations.get() + " ===");

        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("Solution", "Semaphores: resource(1) + mutex(1) + readCount");
        summary.put("Readers / writers / rounds", numReaders + " / " + numWriters + " / " + rounds);
        summary.put("Final shared value", sharedData + " (expected " + expected + ")");
        summary.put("Mutual-exclusion violations", String.valueOf(violations.get()));
        summary.put("Policy", "Readers have priority (writers may starve)");

        String out = Paths.get("generated_html", "readers_writers_semaphore.html").toString();
        SimulationHtmlWriter.write(Paths.get(out), "RW",
                "Readers-Writers (Semaphore)",
                actors, trace.snapshot(), summary);
        System.out.println("HTML simulation written to: " + Paths.get(out).toAbsolutePath());
    }
}

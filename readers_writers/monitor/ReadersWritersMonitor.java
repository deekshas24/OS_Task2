package readers_writers.monitor;

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
 * READERS-WRITERS PROBLEM - MONITOR BASED
 * (writers have priority, so a steady stream of readers cannot starve writers)
 *
 * Java has no "monitor" keyword, but every object has one built in:
 * 'synchronized' methods give mutual exclusion inside the monitor, and
 * wait() / notifyAll() play the role of condition variables.
 *
 * Shared resource : the integer inside ReadWriteMonitor (sharedData).
 * Critical section: the work done between startRead()/endRead() and
 *                   startWrite()/endWrite().
 *
 * Monitor variables:
 *   activeReaders  - readers currently reading
 *   writing        - true while a writer is writing
 *   waitingWriters - writers blocked in startWrite()
 *
 * Conditions (the "while" loops in the monitor):
 *   reader may enter  when  !writing && waitingWriters == 0
 *   writer may enter  when  !writing && activeReaders == 0
 *
 * Run:  java readers_writers.monitor.ReadersWritersMonitor [readers] [writers] [rounds]
 */
public class ReadersWritersMonitor {

    static int numReaders = 4;
    static int numWriters = 2;
    static int rounds = 3;
    static final int MAX_DELAY_MS = 500;

    static final TraceRecorder trace = new TraceRecorder();
    static final Random random = new Random();

    // self-check used only for testing
    static final AtomicInteger readersInside = new AtomicInteger();
    static final AtomicInteger writersInside = new AtomicInteger();
    static final AtomicInteger violations = new AtomicInteger();

    static void pause() throws InterruptedException {
        Thread.sleep(50 + random.nextInt(MAX_DELAY_MS));
    }

    /** The monitor: all synchronization logic lives here. */
    static class ReadWriteMonitor {
        private int sharedData = 0;        // THE SHARED RESOURCE
        private int activeReaders = 0;
        private boolean writing = false;
        private int waitingWriters = 0;

        /** Called by a reader before it reads. Returns the value it read. */
        public synchronized int startRead(String name) throws InterruptedException {
            while (writing || waitingWriters > 0) {      // condition: reader must wait
                wait();                                  // releases the monitor lock while waiting
            }
            activeReaders++;
            if (writersInside.get() != 0) violations.incrementAndGet();
            readersInside.incrementAndGet();
            trace.record(name, "READING", "read value " + sharedData + " (" + activeReaders + " reader(s) inside)", sharedData);
            return sharedData;
        }

        public synchronized void endRead(String name) {
            activeReaders--;
            readersInside.decrementAndGet();
            trace.record(name, "IDLE", "finished reading (" + activeReaders + " reader(s) left inside)");
            if (activeReaders == 0) {
                notifyAll();                             // a waiting writer may now proceed
            }
        }

        /** Called by a writer before it writes. Returns the old value. */
        public synchronized int startWrite(String name) throws InterruptedException {
            waitingWriters++;
            while (writing || activeReaders > 0) {       // condition: writer must wait
                wait();
            }
            waitingWriters--;
            writing = true;
            if (writersInside.incrementAndGet() != 1 || readersInside.get() != 0) {
                violations.incrementAndGet();
            }
            trace.record(name, "WRITING", "read " + sharedData + ", about to write " + (sharedData + 1), sharedData);
            return sharedData;
        }

        public synchronized void endWrite(String name, int newValue) {
            sharedData = newValue;
            writing = false;
            writersInside.decrementAndGet();
            trace.record(name, "IDLE", "wrote value " + sharedData, sharedData);
            notifyAll();                                 // wake up waiting readers and writers
        }

        public synchronized int peek() {
            return sharedData;
        }
    }

    static final ReadWriteMonitor monitor = new ReadWriteMonitor();

    static class Reader extends Thread {
        private final String name;

        Reader(int id) {
            this.name = "R" + id;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < rounds; i++) {
                    pause();
                    trace.record(name, "WAITING", "wants to read");
                    monitor.startRead(name);
                    pause();                              // reading takes time (critical section)
                    monitor.endRead(name);
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
                    int old = monitor.startWrite(name);
                    pause();                              // slow read-modify-write (critical section)
                    monitor.endWrite(name, old + 1);
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

        System.out.println("=== Readers-Writers (Monitor): " + numReaders + " readers, "
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
        int finalValue = monitor.peek();
        System.out.println("=== Finished. Final value = " + finalValue + " (expected " + expected
                + "), mutual-exclusion violations = " + violations.get() + " ===");

        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("Solution", "Monitor: synchronized methods + wait()/notifyAll()");
        summary.put("Readers / writers / rounds", numReaders + " / " + numWriters + " / " + rounds);
        summary.put("Final shared value", finalValue + " (expected " + expected + ")");
        summary.put("Mutual-exclusion violations", String.valueOf(violations.get()));
        summary.put("Policy", "Writers have priority (readers may wait while writers are queued)");

        String out = Paths.get("generated_html", "readers_writers_monitor.html").toString();
        SimulationHtmlWriter.write(Paths.get(out), "RW",
                "Readers-Writers (Monitor)",
                actors, trace.snapshot(), summary);
        System.out.println("HTML simulation written to: " + Paths.get(out).toAbsolutePath());
    }
}

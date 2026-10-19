# OS Synchronization Assignment

**Student name:** `DEEKSHA S`
**USN:** `NNM24IS063`

AI-assisted synchronization algorithms with program-generated HTML simulation:
Readers-Writers and Dining Philosophers (4 philosophers), each solved with semaphores and with monitors.

## The four implementations

| # | Problem | Technique | Source file |
|---|---------|-----------|-------------|
| 1 | Readers-Writers | Semaphores | `readers_writers/semaphore/ReadersWritersSemaphore.java` |
| 2 | Readers-Writers | Monitor | `readers_writers/monitor/ReadersWritersMonitor.java` |
| 3 | Dining Philosophers (4) | Semaphores | `dining_philosophers/semaphore/DiningPhilosophersSemaphore.java` |
| 4 | Dining Philosophers (4) | Monitor | `dining_philosophers/monitor/DiningPhilosophersMonitor.java` |

Shared helper code (used by all four, no synchronization logic inside):

- `common/TraceRecorder.java` - records every state change of every thread, in the order it really happened.
- `common/SimulationHtmlWriter.java` - turns the recorded trace into one standalone HTML file.

### 1. Readers-Writers with semaphores
- Shared resource: the integer `sharedData`. Critical section: reading or writing it.
- `resource` (initial value 1) is the write lock; the first reader takes it for the whole group of readers and the last reader releases it.
- `mutex` (initial value 1) protects the counter `readCount`.
- Readers have priority, so writers can starve if readers keep arriving.

### 2. Readers-Writers with a monitor
- A `ReadWriteMonitor` class with `synchronized` methods and `wait()` / `notifyAll()` (Java's built-in monitor).
- A reader waits while a writer is writing or waiting; a writer waits while anyone is reading or writing.
- Writers have priority, so readers can wait longer when writers keep arriving.

### 3. Dining Philosophers (4) with semaphores
- Four fork semaphores (initial value 1 each) and a `room` semaphore (initial value 3).
- `room` lets only 3 philosophers compete for forks at once, so the circular wait that causes deadlock cannot form.
- All semaphores are fair (first-come first-served), which prevents starvation.

### 4. Dining Philosophers (4) with a monitor
- A `TableMonitor` class; a philosopher picks up both forks together or not at all (no hold-and-wait, so no deadlock).
- Hungry philosophers get a ticket number and cannot overtake an older hungry neighbour (prevents starvation).

## Programming language and tools
- Java 17 or newer (JDK). Tested with OpenJDK 21.
- Eclipse IDE for Java Developers (any recent version), or just a terminal with `javac` and `java`.
- No external libraries.
- Any modern browser (Chrome, Edge, Firefox) to view the simulations.

## Build / compile

**Eclipse**
1. File -> Import -> General -> Existing Projects into Workspace.
2. Select this folder as the root directory and click Finish (the `.project` and `.classpath` files are included).
3. Make sure the project uses JRE 17 or newer (Project -> Properties -> Java Build Path -> Libraries).
4. Eclipse compiles automatically (Project -> Build Automatically).

**Command line** (from the repository root)
```
javac -d bin common/*.java readers_writers/semaphore/*.java readers_writers/monitor/*.java dining_philosophers/semaphore/*.java dining_philosophers/monitor/*.java
```

## Run (and generate the HTML simulation)

Running a program executes the real threads, prints every state change to the console, and **writes the HTML simulation into `generated_html/`** when it finishes (about 4-5 seconds).

**Eclipse:** right-click the file -> Run As -> Java Application. Press F5 on the project afterwards to see the new HTML file in `generated_html/`.

**Command line** (from the repository root)
```
java -cp bin readers_writers.semaphore.ReadersWritersSemaphore
java -cp bin readers_writers.monitor.ReadersWritersMonitor
java -cp bin dining_philosophers.semaphore.DiningPhilosophersSemaphore
java -cp bin dining_philosophers.monitor.DiningPhilosophersMonitor
```

Optional arguments (useful for changing the number of threads or the amount of work and re-running):
```
java -cp bin readers_writers.semaphore.ReadersWritersSemaphore <readers> <writers> <rounds>
java -cp bin dining_philosophers.monitor.DiningPhilosophersMonitor <meals>
```
In Eclipse: Run -> Run Configurations -> Arguments tab. The delay range is the constant `MAX_DELAY_MS` at the top of each program.

Every run produces a different interleaving, so the HTML file changes each time you run the program.

## Open the generated HTML
Double-click the file in `generated_html/` (or drag it into a browser):

- `readers_writers_semaphore.html`
- `readers_writers_monitor.html`
- `dining_philosophers_semaphore.html`
- `dining_philosophers_monitor.html`

Each page has: **Play / Pause / Step / Reset** buttons and a speed slider; an **Actors** panel with one colour-coded card per thread and a legend of states; a **Forks** panel with a round table (Dining Philosophers) or a **Shared resource** panel with active readers, writer status and the shared value (Readers-Writers); and a **Timeline** table of every recorded event, where events not yet replayed are faded. The page contains the recorded trace as data and only replays it; it has no pre-written scenario.

## Expected output
Console (Readers-Writers), last line:
```
=== Finished. Final value = 6 (expected 6), mutual-exclusion violations = 0 ===
```
Console (Dining Philosophers), last line:
```
=== Finished without deadlock. Meals eaten: P0=3, P1=3, P2=3, P3=3; fork-ownership violations = 0 ===
```
Screenshots of the console and of the generated HTML are in `screenshots/`.

## How the programs are tested
- Each program checks its own invariants while running: no writer inside together with a reader or another writer; a fork never held by two philosophers.
- The writers do a slow read-modify-write on the shared value. If mutual exclusion failed, updates would be lost and the final value would be smaller than expected.
- If a Dining Philosophers program deadlocked, it would hang instead of printing the final line.

## Known limitations
- The replay pauses between events follow the real time gaps of the run but are limited to a minimum and maximum so the animation stays watchable.
- Readers-Writers semaphore version: writers can starve (reader priority). Monitor version: readers can wait longer (writer priority). Each program's header comment explains this.
- Java 17 or newer is required (the HTML template uses a text block).

## AI tools used
See `ai_prompts/prompts.md` for the tool names, the prompts and what was changed manually.

## References and acknowledgements
See `references/references.md`.

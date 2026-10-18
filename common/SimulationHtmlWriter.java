package common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a recorded execution trace (list of TraceRecorder.Event) into ONE
 * standalone HTML file that can be opened in any browser.
 *
 * Workflow required by the assignment:
 *   Execute program -> synchronize -> capture state changes -> generate HTML -> view in browser
 *
 * Every row shown in the page's timeline is one event recorded by the running
 * program. For each row this class also works out a snapshot of the shared
 * resource at that moment (who owns each fork, how many readers are inside, is
 * a writer inside). The JavaScript in the page contains no scripted scenario:
 * it only replays the embedded rows (Play / Pause / Step / Reset / speed).
 * Running the program again produces a different trace and a different page.
 */
public class SimulationHtmlWriter {

    /** One thing that appears on screen: a reader, a writer, a philosopher or a fork. */
    public static final class Actor {
        final String id;        // R1, W2, P0, F3 ...
        final String role;      // reader, writer, philosopher, fork
        final String initial;   // state shown before the first event

        public Actor(String id, String role, String initial) {
            this.id = id;
            this.role = role;
            this.initial = initial;
        }
    }

    /**
     * @param file     where to write the HTML file
     * @param kind     "RW" (readers-writers) or "DP" (dining philosophers)
     * @param title    page title
     * @param actors   everything that has a state
     * @param events   the recorded trace
     * @param summary  facts shown in the summary box (for example test results)
     */
    public static void write(Path file, String kind, String title,
                             List<Actor> actors, List<TraceRecorder.Event> events,
                             Map<String, String> summary) throws IOException {

        Map<String, Actor> byId = new HashMap<>();
        int forkCount = 0;
        for (Actor a : actors) {
            byId.put(a.id, a);
            if (a.role.equals("fork")) forkCount++;
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"meta\":{");
        json.append("\"kind\":").append(q(kind)).append(',');
        json.append("\"title\":").append(q(title)).append(',');
        json.append("\"forkCount\":").append(forkCount).append(',');
        json.append("\"actors\":[");
        boolean firstActor = true;
        for (Actor a : actors) {
            if (a.role.equals("fork")) continue;           // forks are shown through the snapshots
            if (!firstActor) json.append(',');
            firstActor = false;
            json.append("{\"thread\":").append(q(threadName(a))).append(",\"type\":").append(q(typeName(a)))
                .append(",\"role\":").append(q(a.role)).append(",\"initial\":").append(q(a.initial)).append('}');
        }
        json.append("],\"summary\":{");
        boolean first = true;
        for (Map.Entry<String, String> e : new LinkedHashMap<>(summary).entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append(q(e.getKey())).append(':').append(q(e.getValue()));
        }
        json.append("}},\"events\":[");

        // ---- replay the trace once to build the per-row snapshot ----
        String[] forkOwner = new String[forkCount];
        Map<String, String> currentState = new HashMap<>();
        boolean firstRow = true;
        for (TraceRecorder.Event e : events) {
            Actor a = byId.get(e.actor);
            if (a == null) continue;
            if (a.role.equals("fork")) {                    // a fork change only updates the snapshot
                int index = Integer.parseInt(a.id.substring(1));
                forkOwner[index] = e.state.equals("TAKEN") ? "Philosopher-" + e.value : null;
                continue;
            }
            currentState.put(a.id, e.state);
            int readers = 0;
            boolean writer = false;
            for (Actor other : actors) {
                String s = currentState.get(other.id);
                if (other.role.equals("reader") && "READING".equals(s)) readers++;
                if (other.role.equals("writer") && "WRITING".equals(s)) writer = true;
            }
            if (!firstRow) json.append(",\n");
            firstRow = false;
            json.append("{\"t\":").append(e.timeMs)
                .append(",\"thread\":").append(q(threadName(a)))
                .append(",\"actor\":").append(q(typeName(a)))
                .append(",\"state\":").append(q(e.state))
                .append(",\"desc\":").append(q(e.note))
                .append(",\"value\":").append(e.value)
                .append(",\"readers\":").append(readers)
                .append(",\"writer\":").append(writer)
                .append(",\"forks\":[");
            for (int i = 0; i < forkCount; i++) {
                if (i > 0) json.append(',');
                json.append(forkOwner[i] == null ? "null" : q(forkOwner[i]));
            }
            json.append("]}");
        }
        json.append("\n]}");

        String html = TEMPLATE
                .replace("__TITLE__", escapeHtml(title))
                .replace("__DATA__", json.toString());

        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.write(file, html.getBytes(StandardCharsets.UTF_8));
    }

    /** R1 -> Reader-1, W2 -> Writer-2, P3 -> Philosopher-3 */
    private static String threadName(Actor a) {
        return typeName(a) + "-" + a.id.substring(1);
    }

    private static String typeName(Actor a) {
        switch (a.role) {
            case "reader": return "Reader";
            case "writer": return "Writer";
            case "philosopher": return "Philosopher";
            default: return "Fork";
        }
    }

    /** Quotes a string as a JSON string. "<" is escaped so the data can never close the script tag. */
    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '<':  sb.append("\\u003c"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------------
    // Page template. __TITLE__ and __DATA__ are filled in above.
    // ------------------------------------------------------------------
    private static final String TEMPLATE = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>__TITLE__</title>
<style>
  /* one colour per state */
  :root {
    --bg:#f6f7f9; --panel:#ffffff; --text:#1d2330; --muted:#6b7280; --border:#dde1e7;
    --WAITING:#f59e0b; --HUNGRY:#ef4444; --READING:#3b82f6; --WRITING:#8b5cf6;
    --EATING:#10b981; --THINKING:#64748b; --DONE:#9ca3af; --IDLE:#cbd5e1;
    --ACQUIRING_FORKS:#f97316; --RELEASING_FORKS:#06b6d4;
  }
  body { margin:0; font-family:system-ui,sans-serif; background:var(--bg); color:var(--text); }
  main { max-width:1100px; margin:0 auto; padding:16px; }
  h1 { font-size:1.4rem; margin:0 0 12px; }
  h2 { font-size:1rem; margin:0 0 8px; }
  section { background:var(--panel); border:1px solid var(--border); border-radius:8px; padding:12px; margin-bottom:12px; }
  .controls { display:flex; flex-wrap:wrap; gap:8px; align-items:center; }
  button { padding:6px 14px; border:1px solid var(--border); border-radius:6px; background:#fff; cursor:pointer; font:inherit; }
  button:hover { background:#eef2f7; }
  #status { color:var(--muted); font-variant-numeric:tabular-nums; }
  #actors { display:grid; grid-template-columns:repeat(auto-fill,minmax(150px,1fr)); gap:8px; }
  .card { border-radius:8px; padding:10px; color:#fff; background:var(--IDLE); transition:background .25s; }
  .card .name { font-weight:600; }
  .card .type, .card .desc { font-size:.8rem; opacity:.92; }
  .card.current { outline:3px solid var(--text); }
  #legend { display:flex; flex-wrap:wrap; gap:6px; margin-top:8px; }
  #table-svg { width:100%; max-width:360px; display:block; margin:0 auto 10px; }
  #table-svg[hidden] { display:none; }
  #resource { display:flex; flex-wrap:wrap; gap:12px; }
  .fork, .stat { border:1px solid var(--border); border-radius:6px; padding:8px 12px; min-width:110px; }
  .fork.held { background:#fde68a; }
  .stat b { font-size:1.4rem; display:block; }
  .table-wrap { max-height:340px; overflow:auto; }
  table { width:100%; border-collapse:collapse; font-size:.85rem; }
  th, td { text-align:left; padding:4px 8px; border-bottom:1px solid var(--border); }
  th { position:sticky; top:0; background:var(--panel); }
  tr.future { opacity:.35; }
  tr.current { background:#fef3c7; }
  .pill { padding:1px 8px; border-radius:10px; color:#fff; font-size:.75rem; }
  .summary td:first-child { color:var(--muted); width:38%; }
</style>
</head>
<body>
<main>
  <h1>__TITLE__</h1>

  <section class="controls">
    <button id="play">Play</button>
    <button id="pause">Pause</button>
    <button id="step">Step</button>
    <button id="reset">Reset</button>
    <label>Speed <input id="speed" type="range" min="1" max="20" value="5"></label>
    <span id="status"></span>
  </section>

  <section><h2>Actors</h2><div id="actors"></div><div id="legend"></div></section>

  <section><h2 id="resource-title">Shared resource</h2>
    <svg id="table-svg" viewBox="0 0 300 300" hidden></svg>
    <div id="resource"></div>
  </section>

  <section><h2>Timeline</h2>
    <div class="table-wrap"><table>
      <thead><tr><th>#</th><th>Time (ms)</th><th>Thread</th><th>Actor</th><th>State</th><th>Description</th></tr></thead>
      <tbody id="timeline"></tbody>
    </table></div>
  </section>

  <section><h2>Run summary</h2><table class="summary"><tbody id="summary"></tbody></table></section>
</main>

<script>
// The trace recorded by the Java program, embedded when the HTML file was generated.
const DATA = __DATA__;
const META = DATA.meta;
const EVENTS = DATA.events;

const $ = id => document.getElementById(id);
const colourOf = state => 'var(--' + state + ', var(--IDLE))';
let pos = 0;          // number of events replayed so far
let timer = null;     // playback timer, null while paused

/* ---------- static parts, built once ---------- */
META.actors.forEach(a => {
  const card = document.createElement('div');
  card.className = 'card';
  card.id = 'card-' + a.thread;
  card.innerHTML = '<div class="name"></div><div class="type"></div><div class="state"></div><div class="desc"></div>';
  card.querySelector('.name').textContent = a.thread;
  card.querySelector('.type').textContent = a.type;
  $('actors').appendChild(card);
});

const statesUsed = new Set(META.actors.map(a => a.initial));
EVENTS.forEach(e => statesUsed.add(e.state));
statesUsed.forEach(s => {
  const pill = document.createElement('span');
  pill.className = 'pill';
  pill.style.background = colourOf(s);
  pill.textContent = s;
  $('legend').appendChild(pill);
});

EVENTS.forEach((e, i) => {
  const tr = document.createElement('tr');
  const cells = [i + 1, e.t, e.thread, e.actor, null, e.desc];
  cells.forEach((value, col) => {
    const td = document.createElement('td');
    if (col === 4) {
      const pill = document.createElement('span');
      pill.className = 'pill';
      pill.style.background = colourOf(e.state);
      pill.textContent = e.state;
      td.appendChild(pill);
    } else {
      td.textContent = value;
    }
    tr.appendChild(td);
  });
  $('timeline').appendChild(tr);
});

Object.entries(META.summary).forEach(([key, value]) => {
  const tr = document.createElement('tr');
  tr.innerHTML = '<td></td><td></td>';
  tr.cells[0].textContent = key;
  tr.cells[1].textContent = value;
  $('summary').appendChild(tr);
});
{
  const tr = document.createElement('tr');
  tr.innerHTML = '<td>Events recorded</td><td></td>';
  tr.cells[1].textContent = EVENTS.length;
  $('summary').appendChild(tr);
}

/* ---------- drawing ---------- */
function render() {
  // latest event of every thread among the first `pos` events
  const latest = {};
  for (let i = 0; i < pos; i++) latest[EVENTS[i].thread] = EVENTS[i];
  const cur = pos > 0 ? EVENTS[pos - 1] : null;

  META.actors.forEach(a => {
    const card = $('card-' + a.thread);
    const e = latest[a.thread];
    card.style.background = colourOf(e ? e.state : a.initial);
    card.querySelector('.state').textContent = e ? e.state : a.initial;
    card.querySelector('.desc').textContent = e ? e.desc : 'not started';
    card.classList.toggle('current', cur !== null && cur.thread === a.thread);
  });

  const box = $('resource');
  box.innerHTML = '';
  if (META.forkCount > 0) {
    $('resource-title').textContent = 'Forks';
    for (let f = 0; f < META.forkCount; f++) {
      const owner = cur ? cur.forks[f] : null;
      const div = document.createElement('div');
      div.className = 'fork' + (owner ? ' held' : '');
      div.textContent = 'Fork ' + f + ': ' + (owner ? owner : 'free');
      box.appendChild(div);
    }
    drawTable(latest, cur);
  } else {
    $('resource-title').textContent = 'Shared resource';
    let value = '-';
    for (let i = 0; i < pos; i++) if (EVENTS[i].value >= 0) value = EVENTS[i].value;
    const readers = cur ? cur.readers : 0;
    const writer = cur ? cur.writer : false;
    const status = writer ? 'LOCKED (writer)' : (readers > 0 ? 'SHARED (readers)' : 'FREE');
    [['Active readers', readers], ['Writer active', writer ? 'YES' : 'no'],
     ['Shared value', value], ['Resource status', status]].forEach(([label, text]) => {
      const div = document.createElement('div');
      div.className = 'stat';
      div.innerHTML = '<b></b><span></span>';
      div.querySelector('b').textContent = text;
      div.querySelector('span').textContent = label;
      box.appendChild(div);
    });
  }

  [...$('timeline').rows].forEach((tr, i) => {
    tr.classList.toggle('future', i >= pos);
    tr.classList.toggle('current', i === pos - 1);
  });
  if (pos > 0) $('timeline').rows[pos - 1].scrollIntoView({ block: 'nearest' });

  $('status').textContent = 'Event ' + pos + ' / ' + EVENTS.length + (cur ? ' - t = ' + cur.t + ' ms' : '');
}

/* Round table: philosopher i sits at seat i, fork i lies between seat i and seat i+1.
   A held fork is drawn next to its owner; a free fork lies on the table. */
function drawTable(latest, cur) {
  const svg = $('table-svg');
  svg.removeAttribute('hidden');       // an SVG element has no .hidden property, so remove the attribute
  svg.innerHTML = '';
  const NS = 'http://www.w3.org/2000/svg';
  const n = META.forkCount, C = 150;
  const add = (tag, attrs, text) => {
    const el = document.createElementNS(NS, tag);
    for (const k in attrs) el.setAttribute(k, attrs[k]);
    if (text !== undefined) el.textContent = text;
    svg.appendChild(el);
    return el;
  };
  const point = (angle, radius) => [C + radius * Math.cos(angle), C + radius * Math.sin(angle)];
  const seatAngle = i => -Math.PI / 2 + 2 * Math.PI * i / n;
  const seats = META.actors.filter(a => a.role === 'philosopher');

  add('circle', { cx: C, cy: C, r: 80, fill: '#e7d8c3', stroke: '#b59b78', 'stroke-width': 3 });

  for (let f = 0; f < n; f++) {
    const owner = cur ? cur.forks[f] : null;
    let angle = seatAngle(f) + Math.PI / n;                    // halfway between seat f and seat f+1
    if (owner) {
      const s = seats.findIndex(p => p.thread === owner);
      angle = seatAngle(s) + (s === f ? 0.35 : -0.35);         // move next to the owner
    }
    const [x1, y1] = point(angle, owner ? 70 : 45);
    const [x2, y2] = point(angle, owner ? 98 : 72);
    add('line', { x1, y1, x2, y2, stroke: owner ? '#b45309' : '#9ca3af', 'stroke-width': 6, 'stroke-linecap': 'round' });
    const [tx, ty] = point(seatAngle(f) + Math.PI / n, 28);
    add('text', { x: tx, y: ty, 'text-anchor': 'middle', 'dominant-baseline': 'middle', 'font-size': 10, fill: '#6b7280' }, 'F' + f);
  }

  seats.forEach((p, i) => {
    const e = latest[p.thread];
    const [x, y] = point(seatAngle(i), 125);
    const circle = add('circle', { cx: x, cy: y, r: 22, 'stroke-width': 3,
                                   stroke: cur && cur.thread === p.thread ? '#1d2330' : '#ffffff' });
    circle.style.fill = colourOf(e ? e.state : p.initial);
    add('text', { x, y, 'text-anchor': 'middle', 'dominant-baseline': 'middle', 'font-size': 10,
                  fill: '#ffffff', 'font-weight': 600 }, 'P' + p.thread.split('-')[1]);
  });
}

/* ---------- playback ---------- */
function step() {
  if (pos < EVENTS.length) { pos++; render(); }
  if (pos >= EVENTS.length) pause();
}
function play() {
  pause();
  if (pos >= EVENTS.length) pos = 0;
  timer = setInterval(step, 1000 / Number($('speed').value));
}
function pause() { if (timer) { clearInterval(timer); timer = null; } }
function reset() { pause(); pos = 0; render(); }

$('play').onclick = play;
$('pause').onclick = pause;
$('step').onclick = () => { pause(); step(); };
$('reset').onclick = reset;
$('speed').oninput = () => { if (timer) play(); };

render();
</script>
</body>
</html>
""";
}

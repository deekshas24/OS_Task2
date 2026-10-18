# AI prompt log

## AI tool(s) used
- Claude (Anthropic), used through the Claude app. Model name as shown in the app: `[CONFIRM FROM YOUR CLAUDE APP, e.g. Claude Sonnet 5.5]`.
- Chromium (headless) was used by the AI to open the generated HTML and check that it renders and replays.

## Initial prompt
> Implement Producer, Consumer and the Dining philosophers problem using the following using the language of your choice. 1. Semaphores 2. Monitors
> (I am a 3rd-year ISE B.Tech student; I want Java code that runs in Eclipse.)

Result: the AI produced Producer-Consumer and 5-philosopher Dining Philosophers programs. These did not match the final assignment sheet (see reflection below).

## Important follow-up prompts
1. "Are you sure that the code is perfectly correct? ..." - the AI re-ran each program five times, found that a code comment overclaimed starvation freedom, and corrected it.
2. "Make sure it is done in this way according to this PDF ..." (the assignment sheet `OS_AI_Synchronization_Assignment.pdf` attached) - the AI re-read the sheet and rebuilt the work: Readers-Writers and exactly 4 philosophers, each with semaphores and monitors, plus program-generated HTML simulations, README, prompt log and references.

3. "I WANT OUTPUT TO LOOK LIKE THIS" (a sample `dining_philosophers_monitor.html` attached) - the AI rewrote the HTML generator so the pages use the same layout: control buttons with a speed slider, colour-coded actor cards, a round table with fork ownership, and a faded timeline table. In testing the AI found and fixed two display bugs (a blank gap on the Readers-Writers pages and a hidden round table).

`[ADD EVERY OTHER PROMPT YOU USE FROM NOW ON - this log must be complete and honest.]`

## What I changed manually
`[WRITE THIS YOURSELF after you have read and modified the code. Examples of the kind of thing to record: changed the number of readers/writers or the delay range and re-ran; renamed variables; added comments in my own words; changed the number of meals; the exact lines you edited and why.]`

## Reflection: errors, limitations, improvements found in the AI output
- **Wrong problem set in the first answer:** the first version solved Producer-Consumer and a 5-philosopher table because I had not yet given the full assignment sheet. The AI should have asked for the full sheet first.
- **Overclaiming comment:** the first monitor Dining Philosophers comment said the classic solution avoids starvation. It only avoids deadlock; a philosopher can be overtaken repeatedly. The final monitor version adds ticket ordering for hungry neighbours.
- **Testing is evidence, not proof:** the programs finished correctly in 20 of 20 repeated runs, but thread timing varies, so passing runs do not prove there is no deadlock. As a check that the deadlock prevention matters, the semaphore Dining Philosophers program with the `room` semaphore raised from 3 to 4 deadlocked in 8 of 12 runs, while the real program finished every time.
- **Limitation:** the HTML replay compresses long real-time gaps so the animation stays watchable.
`[ADD YOUR OWN OBSERVATION HERE.]`

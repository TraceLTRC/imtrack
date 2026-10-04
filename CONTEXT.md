# imtrack

A personal time-tracking app for people who practise anti-scheduling: it records how much time you spend on the things you care about, so you can see yourself improving against your own past.

## Language

**Anti-scheduling**:
A productivity method where fixed commitments are placed first and self-directed work fills the gaps between them. There are no targets: zero is the baseline, and any tracked time is progress.
_Avoid_: Time-blocking, goal-setting

**Self-comparison**:
Measuring progress only against your own past (e.g. this week vs. last week). imtrack deliberately has no comparison against other people.
_Avoid_: Leaderboard, ranking

**Project**:
A long-running thing you put time into, such as learning 3D modelling or building a prop gun. It may never be "done" and has no target. Projects are flat (no sub-projects). Projects are never deleted, only archived.
_Avoid_: Goal, Task, Category, Pursuit

**Archive** (verb):
To retire a Project. An archived Project disappears from Home and can't be logged to or have its Stopwatch started, but its Sessions stay in History and stats. Archiving can be undone (unarchive).
_Avoid_: Delete, Hide

**Session**:
An amount of time spent on a single Project on a given day: a date plus a duration, with no start or end time. A Session is created either by running the Stopwatch or by logging after the fact, and it can be edited afterwards. Its duration is stored in seconds but always shown in whole minutes, rounded up; totals add up the seconds first and round once at the end.
_Avoid_: Entry, Time entry, Log (as a noun)

**Log** (verb):
To record a Session after the fact by stating its duration, without having run the Stopwatch.

**Stopwatch**:
The live timer that produces a Session when stopped. It is a convenience: logging after the fact is the primary way to record time. At most one Stopwatch runs at a time; starting one for another Project stops the current one. It has no pause, because stopping and starting again just creates another Session. Stopping it before one minute asks whether to discard or keep the Session.
_Avoid_: Timer (ambiguous with countdowns)

**Streak**:
The number of consecutive days with more than zero minutes tracked, counted across all Projects together. There are no per-Project streaks.

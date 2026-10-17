# The Stopwatch is a saved start time, not a foreground service

A running Stopwatch is just a stored start time; elapsed time is computed whenever it's shown. Its ongoing notification uses the system chronometer (`setUsesChronometer`), which the system draws, so no app process has to stay alive and doze doesn't matter. A Stop action and the notification's delete intent are handled by a broadcast receiver. Notifications and alarms are lost on reboot and force-stop, so an idempotent reconcile step runs on `BOOT_COMPLETED` and on every app start: if a Stopwatch is running it re-posts the notification and reschedules the Reminder, otherwise it clears both.

## Considered Options

- **Foreground service**: the usual choice for a stopwatch, but Android 14+ needs a foreground service type and none fits (`specialUse` needs Play review justification, `shortService` is capped at 3 minutes, `dataSync` at 6 hours on Android 15). It costs battery and code and adds nothing, because nothing needs to run while the Stopwatch counts.

## Consequences

- Without a UI, the under-a-minute question can't be asked: stopping from the notification, or swiping the notification away, always keeps the Session.
- The Reminder uses inexact `AlarmManager.setAndAllowWhileIdle`, not exact alarms or WorkManager.

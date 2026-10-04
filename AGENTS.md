# imtrack

A time-tracking app for people who practise anti-scheduling. It's a native Android app (Kotlin, Jetpack Compose, Hilt, Room); see `docs/adr/`.

## Working agreement

Agents may write app code (anything under `app/`, Gradle files). The human generates the project skeleton with Android Studio's New Project wizard; everything after that can be agent-written.

- Agents leave their changes uncommitted. The human reviews the diff in Android Studio, runs the app, and commits. Every issue is verified by the human, whoever implemented it.
- Add a Compose `@Preview` for each screen and reusable component where practical (e.g. empty, populated, dark). Don't contort app code just to make a preview work; the debug build covers what previews can't.
- Take library versions from official docs or release pages, not from memory.

## Agent skills

### Issue tracker

Issues live in GitHub Issues on `TraceLTRC/imtrack`, managed via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

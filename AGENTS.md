# imtrack

A time-tracking app for people who practise anti-scheduling. It's a native Android app (Kotlin, Jetpack Compose, Hilt, Room); see `docs/adr/`.

## Working agreement: the human drives, the agent navigates

The human writes all app code by hand, so they understand every line. Agents act as the **navigator** in pair programming: explain, plan, review, and show code in chat for the human to type. Agents edit `CONTEXT.md`, `docs/` and GitHub issues freely. Agents edit source files (anything under `app/`, Gradle files) only when the human explicitly asks for that edit.

## Agent skills

### Issue tracker

Issues live in GitHub Issues on `TraceLTRC/imtrack`, managed via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

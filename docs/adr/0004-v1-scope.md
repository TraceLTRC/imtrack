# v1 scope: what is deliberately left out

v1 covers Projects, Sessions (logging after the fact plus the Stopwatch with its notification), Session history, per-period totals with comparison, the overall Streak, the heatmap, and JSON export/import. The following are **out of v1 on purpose**. They are not oversights, so don't add them without reopening this decision:

- **Media and catalog lookups (AniList etc.) and importing history from the Discord bot**: Japanese immersion stays in the Discord bot, which already covers it. Media needs its own design pass.
- **Home-screen widget and Quick Settings tile**: nice ways to reach the app faster, but the ongoing notification covers the core need. They have their own design topic.
- **Sync, accounts and a PC client**: built only when actually needed; ADR-0002 keeps the schema ready for them.
- **Personal records and charts**: the second wave of rewards, after the Stats & rewards design settles the basics.
- **Notes on Sessions**: logging stays at two fields (Project, minutes) to keep it effortless.
- **Anything social** (leaderboards, friends): anti-scheduling is Self-comparison. This one is likely out permanently, not just for v1.

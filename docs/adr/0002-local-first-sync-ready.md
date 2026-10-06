# Local-first storage with a sync-ready schema, no backend in v1

The phone is the source of truth. All data lives in on-device SQLite (via Room) and the app works fully offline. v1 has no accounts and no server; manual JSON export/import is the backup. Every record still carries a globally unique ID (UUID, not an auto-increment integer) and an updated-at timestamp, and deletions leave a marker (soft delete) instead of removing the row. That means multi-device sync and multi-user support can be added later without migrating identities or losing deletions. The author prefers to build sync "when it's needed", but doesn't want the schema to block it.

## Exception: Import

JSON Export writes every row, including soft-deleted ones. Import replaces all data: in one transaction it hard-deletes every row and inserts the file's rows unchanged (same IDs, timestamps and `deletedAt`). This is the only hard delete. It is a restore from backup rather than a user deleting something, and the file still carries every deletion marker.

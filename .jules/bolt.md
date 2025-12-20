## 2024-05-23 - Optimizing List Selection State
**Learning:** Passing a `SnapshotStateList` (or a `List` derived from it) to every item in a `LazyColumn` causes all visible items to recompose when the list changes (e.g. adding one selection).
**Action:** Use `derivedStateOf { key in list }` in the item scope and pass a simple `Boolean` to the item composable. This isolates the recomposition to only the items whose selection state actually changed.

## 2024-05-24 - Database Indexing for Filtering
**Learning:** Frequent queries filtering by foreign keys (e.g. `assistant_id`, `conversation_id`) without indices cause full table scans. This is especially impactful in `ChatEpisodeEntity` which is frequently queried by `conversation_id`.
**Action:** Always check `WHERE` clauses in DAOs and ensure corresponding columns are indexed, especially for foreign keys and columns used in sorting. Added indices to `ConversationEntity` and `ChatEpisodeEntity`.

## 2024-05-23 - Optimizing List Selection State
**Learning:** Passing a `SnapshotStateList` (or a `List` derived from it) to every item in a `LazyColumn` causes all visible items to recompose when the list changes (e.g. adding one selection).
**Action:** Use `derivedStateOf { key in list }` in the item scope and pass a simple `Boolean` to the item composable. This isolates the recomposition to only the items whose selection state actually changed.

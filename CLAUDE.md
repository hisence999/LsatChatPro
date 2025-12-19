# CLAUDE.md

## Project Overview
**App:** LastChat (Native Android LLM Client)
**Repo:** RikkaHub
**Philosophy:** "Fidget Toy" feel. Playful, physics-based, tactile.

## Critical Rules for Claude
1.  **Haptics:** NEVER use `LocalHapticFeedback`. ALWAYS use `rememberPremiumHaptics()` and `HapticPattern`.
2.  **Animation:** NEVER use `tween`/linear. ALWAYS use `spring` (Standard: `0.5f/400f`).
3.  **Concurrency:** ALL I/O must use `Dispatchers.IO`. `AppScope` is `Default`.
4.  **Safety:** NEVER use `!!` on JSON. Use `JsonInstant` for serialization.
5.  **Shapes:** Use `AppShapes` (e.g., `CardLarge`, `ButtonPill`).

## Architecture
-   **Stack:** Kotlin, Jetpack Compose (M3 Expressive), Koin, Room.
-   **Modules:** `app` (UI/Core), `ai` (SDKs), `common` (Utils).

## Key Patterns
-   **Buttons:** Scale to `0.85f` on press + `HapticPattern.Pop`.
-   **State:** Snapshot `StateFlow` before updating in services.
-   **Lists:** Use `derivedStateOf` for item state in `LazyColumn`.

*For detailed guidelines on philosophy, security, and specific implementations, READ `AGENTS.md`.*

# AI / Architecture Rules

These rules protect the integrity of Java Farmer while multiple agents or developers contribute.

## Protected boundaries

### 1. Player Java execution

`JavaProgramRunner` is security-sensitive. Player programs are compiled and executed in a child JVM. `SafeProgramValidator` allows only a small Java grammar and the `FarmerBot` API.

Allowed grammar (0.3.0): one class `PlayerProgram`; `static` methods and `static` fields only; types limited to primitives, `String` and arrays of them (max 2 dimensions); local variables, if/else, for, for-each, while, do-while, ternary, break/continue/return (no labels); array creation/access and `.length`; calls only to `FarmerBot.*` (allowlisted names), `Math.{abs,min,max,floorMod,floorDiv,sqrt,pow,round,random}`, the player's own declared methods, and a small set of String methods on values (`equals`, `length`, `isEmpty`, `contains`, ...) whose receiver is never a class name. Object creation, other classes, lambdas, instance methods, constructors, annotations, imports other than `FarmerBot`, `switch`, `try`/`throw` and labels stay rejected. Any widening of this list needs a test in `tools/CoreSmokeTest.java` (see `validatorRejectsUnsafeCode`).

Sensors: the game writes a `Farm.snapshot()` text file into the program's private temporary directory; only the trusted `FarmerBot` class reads it (player code cannot touch files). The child replays actions on a local `Farm` with the same rules, and the game replays the emitted actions on its authoritative `Farm`. Sensors emit no actions.

Preserve:

- Maximum source length: 18,000 characters.
- Child-process heap: 64 MiB.
- Execution timeout: 3 seconds.
- Maximum recognized actions: 250.
- Child-process output limit: 256 KiB, enforced while reading the output stream (not only after process exit).
- No arbitrary imports, reflection, filesystem, network, process, classloader or native APIs.

Do not claim that process isolation alone is a complete sandbox. For public/untrusted multiplayer code, move execution to an OS-level sandbox or isolated server environment before release.

### 2. Public gameplay API

The player-facing API in `com.javafarmer.api.FarmerBot` is part of the learning curriculum. Changes must include:

1. A migration or compatibility alias.
2. Updated in-game examples.
3. Updated tests and documentation.

### 3. Persistence

`GameSave` is a local save schema (current version 2). New fields must have safe defaults. Removed or changed fields require explicit migration and a backup strategy. Conversion and migration live in `save/SaveMapper`; v1 saves (0.1/0.2) are migrated on load (seeds → wheat seeds, cycles → ticks, growth stage → growth ticks).

### 4. Game rules

All rules that affect the farm (time costs, growth, ownership, economy) live in `world/Farm` and `world/FarmWorld`, which must stay pure Java: they run both in the game and in the player's child JVM, and the two copies must produce identical results.

### 5. Platform support

Do not add platform-specific code to the `core` module. OS-specific startup and packaging belong in `lwjgl3` and the packaging scripts. Native dependencies must remain aligned to LWJGL 3.4.3 unless a verified upgrade is performed.

### 6. Visual and asset policy

Keep the Cozy Clay visual direction: rounded 3D silhouettes, low visual noise, restrained palette and soft light. Reference games may inspire broad mechanics only; never extract or copy their proprietary game assets.

## What these rules cannot do by themselves

A Markdown instruction file is not technically immutable: an agent with write access could edit or delete it. Enforce the rules in the Git hosting platform as well:

- Protect `main` with a repository ruleset.
- Require pull requests and at least one human approval.
- Require CI checks to pass before merge.
- Disable force-push and branch deletion on `main`.
- Restrict who can change workflows, security code and branch rules.

The repository owner must enable these settings; the project files cannot enforce host-level permissions alone.

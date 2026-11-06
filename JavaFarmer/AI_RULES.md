# AI / Architecture Rules

These rules protect the integrity of Java Farmer while multiple agents or developers contribute.

## Protected boundaries

### 1. Player Java execution

`JavaProgramRunner` is security-sensitive. Player programs are compiled and executed in a child JVM. `SafeProgramValidator` allows only a small Java grammar and the `FarmerBot` API. Preserve:

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

`GameSave` is a local save schema. New fields must have safe defaults. Removed or changed fields require explicit migration and a backup strategy.

### 4. Platform support

Do not add platform-specific code to the `core` module. OS-specific startup and packaging belong in `lwjgl3` and the packaging scripts. Native dependencies must remain aligned to LWJGL 3.4.3 unless a verified upgrade is performed.

### 5. Visual and asset policy

Keep the Cozy Clay visual direction: rounded 3D silhouettes, low visual noise, restrained palette and soft light. Reference games may inspire broad mechanics only; never extract or copy their proprietary game assets.

## What these rules cannot do by themselves

A Markdown instruction file is not technically immutable: an agent with write access could edit or delete it. Enforce the rules in the Git hosting platform as well:

- Protect `main` with a repository ruleset.
- Require pull requests and at least one human approval.
- Require CI checks to pass before merge.
- Disable force-push and branch deletion on `main`.
- Restrict who can change workflows, security code and branch rules.

The repository owner must enable these settings; the project files cannot enforce host-level permissions alone.

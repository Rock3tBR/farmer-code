# Agent Operating Instructions — Java Farmer

This file is the entry point for any coding agent or AI assistant working in this repository. Read it before changing files.

## Current project status

Java Farmer is a Java 25 + libGDX 1.14.2 + LWJGL 3.4.3 desktop game. The repository contains a vertical slice (0.2.0, Cozy Clay visuals), not the complete long-term game. Rendering lives in `core/render`, UI helpers in `core/ui`. The Gradle desktop build still needs validation on JDK 25/Gradle 9.8.2 on Windows and macOS.

## Mandatory workflow

1. Read `README.md`, `AI_RULES.md`, `docs/ARCHITECTURE.md`, and `docs/ROADMAP.md`.
2. Inspect the existing code before proposing a rewrite.
3. Make one coherent change at a time; do not replace the project wholesale.
4. Run `tools/smoke-test.sh` on macOS/Linux or `tools/smoke-test.bat` on Windows.
5. Run `python tools/asset_audit.py` after modifying assets.
6. Report what was actually run and what could not be tested.
7. Update documentation whenever commands, dependencies, saves, or user-facing behavior change.

## Non-negotiable engineering rules

- Keep the game desktop-compatible with Windows 10/11 and macOS Intel/Apple Silicon.
- Keep gameplay, world state, code execution and rendering separated by package/module responsibility.
- Preserve the Java API `com.javafarmer.api.FarmerBot`; introduce migrations before removing or renaming API calls.
- Never execute player source directly inside the game process.
- Do not remove or weaken `JavaProgramRunner` AST validation, process timeout, memory limits, source-size limit or action limit without an approved security design and tests.
- Do not expose filesystem, network, reflection, process, classloader, native or arbitrary Java APIs to player programs.
- Never use user-provided code as Gradle build logic or shell commands.
- Do not store credentials, access tokens, personal data or secrets in source code or save files.
- Do not copy proprietary models, textures, sounds, code or level layouts from reference games. Use references for broad design principles only.
- Keep save schema backward-compatible. If a field changes meaning or is removed, add a versioned migration.
- Avoid unbounded per-frame allocations, asset loading inside render loops, and unbounded caches.
- All created models, textures, sounds, fonts and batches must have a clear disposal owner.
- Prefer lightweight procedural assets and compressed textures. Add an audit for every new asset type.
- No direct push to `main`; use a feature branch and pull request.

## Definition of done

A change is not complete because it compiles on one machine. Completion requires relevant functional tests, asset audit when applicable, documented cross-platform caveats, and a clear list of tested OS/JDK/Gradle versions. Do not claim a Windows or macOS build was tested unless it really was.

## Agent report format

At the end of each task, report:
- Changed files and behavior.
- Commands/tests actually executed and their results.
- Security/performance implications.
- Known limitations and the next recommended task.

# AGENTS.md

This file provides guidance when working with code in this repository.

## TL;DR

- Do not create git branches unless explicitly instructed.
- Run `./check-ci.sh` before handing work back.

## Project

Takeup is an Android media player for Loom.

Single-developer hobby project - prefer simple, maintainable solutions over clever abstractions.

Loom and Takeup are developed and deployed together for one user. Do not preserve compatibility with older versions of either application; make coordinated changes in both repositories instead of adding compatibility shims.

## Critical Expectations

- Apply YAGNI ("You Aren't Gonna Need It") and KISS ("Keep It Simple, Stupid"). Build only what the current task requires; do not add abstractions, generality, or future-proofing for needs that do not yet exist. When two approaches work, take the simpler one.
- Prefer self-documenting code and local comments over separate documentation. Comments should explain non-obvious constraints, tradeoffs, invariants, historical context, or surprising decisions rather than restating the code.
- Prefer opinionated defaults over exposing more user-facing configuration. Add configuration only when there is a clear recurring need.
- Coordinate major tradeoffs with the user; never unilaterally defer functionality.
- Keep edits ASCII unless the file already uses extended characters.
- When troubleshooting, gather evidence and test rather than guessing.
- Add focused tests for new behavior and regressions.
- Follow established Android and project conventions. Do not add libraries, frameworks, or architectural layers without a concrete need.
- Follow Material You design standards. Research them if needed.

## Build, Test, Lint

Run the canonical local tests, Android lint, and debug build:

```bash
./check-ci.sh
```

Run device tests separately when an emulator or device is available:

```bash
./gradlew connectedCheck
```

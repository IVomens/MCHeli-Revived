# Code style

MCHeli Revived is a large codebase with a long history (1.7 → 1.12 → 1.21). These rules keep it
readable for contributors and addon authors.

## Naming

* Classes use plain descriptive names with conventional suffixes: `PlaneEntity`, `PlaneRenderer`,
  `PlaneItem`, `PlaneHud`, `WeaponSpec`. No `MCH_`/`W_` style prefixes.
* Methods say what they do: `canFire()`, `applyRecoil()`, `findNearestTarget()`. Avoid `doIt`,
  `process2`, `func`, `handleStuff`.
* Fields and locals are words, not abbreviations, unless the abbreviation is universal in the
  domain (`hp`, `yaw`, `rpm`, `aoa`). No numbered variables (`d0`, `f3`, `var12`, `par1`).
* Constants are `UPPER_SNAKE_CASE` and replace magic numbers whenever the number has a meaning.

## Structure

* One responsibility per class. When a class grows past roughly 800 lines, look for a cohesive
  piece to extract (a physics step, a weapon-selection model, a sync helper).
* Methods should fit on a screen. Long `tick()` bodies become a sequence of well-named steps.
* Prefer early returns over deep nesting.
* No dead code: no commented-out blocks, no unused fields, methods, parameters or imports, no
  empty overrides that only call `super`.
* No copy-paste: shared logic between entity types lives in the base class or a helper.
* Keep side-specific code apart: client-only code lives in classes that are only loaded on the
  client and are reached through the proxy or client event handlers.

## Comments

* Comments explain *why*, not *what*. Good code mostly explains itself.
* Write them the way a teammate would: short, plain sentences. No changelog-style history
  ("ported from", "was X in 1.12", "fixed by"), no marketing language, no headings made of dashes.
* Javadoc on public API that addon or integration authors are expected to use, and on anything
  non-obvious. Skip boilerplate javadoc that restates the method name.
* All comments are in English.

## Formatting

* 4-space indentation, 120 columns, braces on the same line.
* Imports are explicit (no wildcards) and unused imports are removed.
* `final` on fields that never change after construction.
* Use `var` only when the type is obvious from the right-hand side.

## Performance

* No chunk loading from game logic. Check `level.hasChunkAt(pos)` instead.
* No full-level entity scans per tick. Narrow the box, cache the result for a few ticks, or keep an
  index.
* No allocation in per-frame or per-tick hot paths when a reusable object will do.
* Network: send only what changed, to players that track the entity.

## Behaviour

Refactoring never changes gameplay. Vehicle definitions (`*.yml`/`*.txt`), saved worlds (NBT keys)
and config files must keep loading exactly as before.

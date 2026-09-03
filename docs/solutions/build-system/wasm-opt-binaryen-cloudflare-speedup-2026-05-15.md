---
title: "wasmJs Build Speedup — BinaryenExec wasm-opt Tuning for Cloudflare Workers Builds"
date: 2026-05-15
type: build-system
modules: [composeApp, cloudflare-workers-builds]
keywords: [wasm-opt, binaryen, BinaryenExec, binaryenArgs, cloudflare-workers-builds, gradle, build-speedup, wasmjs, ci-timeout, compileProductionExecutableKotlinWasmJsOptimize, kotlin-wasm]
project: Checklists
---

# wasmJs Build Speedup — BinaryenExec wasm-opt Tuning

## TL;DR

The single biggest knob for shrinking a Kotlin/Wasm CI build time is the
**`BinaryenExec` (`wasm-opt`) optimization pass list**. Kotlin's default runs
7 heavy passes single-threaded; replacing them with one `-O2` cuts the
`compileProductionExecutableKotlinWasmJsOptimize` task from minutes to
seconds, at the cost of a ~10–20% larger `.wasm` file.

This is **build-script config, not a doc-discoverable setting** — it lives in
`composeApp/build.gradle.kts`, ~30 lines. If someone asks "how did we speed up
the wasmJs build", the answer is here AND in that file — grep `BinaryenExec`.

## The optimization

Location: **`composeApp/build.gradle.kts`** (top-level `tasks.withType` block).

```kotlin
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenExec

tasks.withType<BinaryenExec>().configureEach {
    binaryenArgs = mutableListOf(
        // Feature flags — required by Kotlin/Wasm-GC bytecode (do NOT drop)
        "--enable-gc",
        "--enable-reference-types",
        "--enable-exception-handling",
        "--enable-bulk-memory",
        "--enable-nontrapping-float-to-int",
        "--closed-world",
        // No-inline directives — correctness (preserve exception unwinding)
        "--no-inline=kotlin.wasm.internal.throwValue",
        "--no-inline=kotlin.wasm.internal.getKotlinException",
        "--no-inline=kotlin.wasm.internal.jsToKotlinStringAdapter",
        // Cheap optimization toggles
        "--inline-functions-with-loops",
        "--traps-never-happen",
        "--fast-math",
        // Single -O2 pass instead of 5x -O3 + --gufa + --type-merging + -Oz
        "-O2",
    )
}
```

## What it replaces

Kotlin 2.3.20 default `binaryenArgs` (from `kotlin-gradle-plugin`
`BinaryenConfig`) runs **7 optimization passes**:

```
--type-ssa -O3 -O3 --gufa -O3 --type-merging -O3 -Oz
```

Each `-O3` is a full optimization sweep; `--gufa` (Grand Unified Flow
Analysis) and `--type-merging` are individually expensive. `wasm-opt` is
**single-threaded**, so on a 2-vCPU CI runner this serial chain dominates the
build.

The fix keeps every `--enable-*` feature flag (mandatory — Wasm-GC bytecode
won't load without them), keeps the `--no-inline` correctness directives
(they protect exception unwinding), and collapses the 7 passes into one
`-O2`.

## Why it matters

| | Default (7 passes) | `-O2` single pass |
|---|---|---|
| `...KotlinWasmJsOptimize` task | minutes (scales with app `.wasm` size) | ~30–90 s |
| Output `.wasm` size | baseline | +~10–20% |
| Runtime perf | maximal | marginally lower (negligible for Compose UI) |
| Correctness | — | unchanged (same feature flags + no-inline) |

Trade-off is **bundle size for build time**. For a web app shipped through
Cloudflare Workers Builds (hard 20-minute build timeout, not raisable), build
time is the binding constraint. The size bump must stay under Cloudflare's
**25 MiB per-file** static-asset limit — check the largest `.wasm` after
changing this.

## Reusability — proven cross-project

Applied identically in **swapfaceandroid** on 2026-05-15: its 36-module
wasmJs build hit ~22 min (over the 20-min Cloudflare limit), with
`compileProductionExecutableKotlinWasmJsOptimize` alone eating ~8m51s — 40%
of the build. The same snippet (verbatim — both projects on Kotlin 2.3.20)
dropped that task to ~30–90 s. See swapfaceandroid
`docs/solutions/ci-deploy/wasmjs-binaryen-opt-cloudflare-speedup-2026-05-15.md`.

## Gotchas

- **`BinaryenExec` import path changed in Kotlin 2.2.0.** Now
  `org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenExec` (was
  `...targets.js.binaryen.BinaryenExec`). On Kotlin < 2.2 use the old path.
- **Default `binaryenArgs` can change between Kotlin versions.** The block
  *replaces* the whole list — re-check the feature flags against the current
  Kotlin release on every Kotlin bump. Missing an `--enable-*` flag → the
  `.wasm` fails to instantiate in the browser.
- **Do not drop `--no-inline=kotlin.wasm.internal.*`.** They keep exception
  unwinding correct; dropping them can corrupt error handling.
- **Android is unaffected** — `BinaryenExec` tasks exist only in the wasmJs
  pipeline. `tasks.withType<BinaryenExec>` matches nothing in an Android
  build.
- **Place the block in the module that produces `wasmJsBrowserDistribution`**
  (here `composeApp`; in swapfaceandroid it's `:app`).

## Verification

```bash
./gradlew composeApp:wasmJsBrowserDistribution --dry-run   # config valid
./gradlew composeApp:wasmJsBrowserDistribution             # real timing
# inspect: compileProductionExecutableKotlinWasmJsOptimize duration
# inspect: largest .wasm file size < 25 MiB
```

## References

- [Kotlin Slack — "longest task is compileProductionExe"](https://slack-chats.kotlinlang.org/t/22745091/)
- [Binaryen GC Optimization Guidebook](https://github.com/WebAssembly/binaryen/wiki/GC-Optimization-Guidebook)
- [Kotlin/Wasm docs](https://kotlinlang.org/docs/wasm-overview.html)
- Cloudflare deploy: `docs/solutions/features/wasmjs-web-target-cloudflare-2026-05-08.md`

---
date: 2026-07-15
title: classes.jar file lock when two Gradle invocations overlap (Windows)
severity: low
type: build-infrastructure
status: open
---

# `classes.jar` locked by another process

## Problem

With two Gradle invocations overlapping (a background run from an agent/IDE plus a CLI one), the second fails:

```
Execution failed for task ':feature:home:bundleAndroidMainClassesToRuntimeJar'.
> java.nio.file.FileSystemException: ...\feature\home\build\intermediates\runtime_library_classes_jar\
  androidMain\bundleAndroidMainClassesToRuntimeJar\classes.jar:
  Процесс не может получить доступ к файлу, так как этот файл занят другим процессом
```

Observed on `:feature:home` (a KMP library module) during the 2026-07-15 session — twice in a row, while a background test run held the file. Windows-only class of failure (no `unlink`-while-open semantics).

Important: this is a **file lock, not a code failure**. The compile step succeeds; only the jar packaging trips. Misreading it as a broken build wastes a diagnostic cycle.

## Workaround (works, verified)

```bash
./gradlew --stop   # stops all daemons, releases handles
```

then re-run. In the session above this cleared it immediately (2 daemons stopped).

## Not yet investigated

Whether a permanent fix is worth it, and which. Candidates, none of them validated here:
- keep concurrent invocations from overlapping on the same module (workflow-level discipline);
- check whether Windows Defender / Controlled Folder Access holds the handle open (there is precedent in this repo for CFA breaking file access — see memory `gcloud-deploy-cloudsdk-config-cfa-workaround`);
- Gradle daemon / file-watching settings.

Do not copy JVM-tuning snippets into `gradle.properties` hoping it helps — the mechanism here is a held file handle, not heap pressure.

## Verification

Start a long build in the background, run a second one against the same module while it runs, and see whether the second still trips on `classes.jar`.

---
name: build-jar
description: Build java-atk-wrapper.jar with the bundled native library, and optionally copy it to a target path. Use when asked to build, rebuild, or install the jar, or to test a wrapper change in an application.
---

# Build the jar

Run the script from the repository root:

```bash
.agents/skills/build-jar/build-jar.sh                       # incremental build
.agents/skills/build-jar/build-jar.sh --clean               # clean build
.agents/skills/build-jar/build-jar.sh --clean --install <directory or jar path>
```

The result is `wrapper/java-atk-wrapper.jar`. The script prints the bundled native library and the SHA-256 of the
jar. With `--install`, it saves the old jar to a temporary file and prints its path.

## When to use `--clean`

Use `--clean` after a branch switch. The jar takes every `wrapper/org/GNOME/Accessibility/*.class` by a wildcard,
so an incremental build can pack a class that the current branch does not have. An incremental build is correct
when you only edited files on the same branch.

## Facts about the build

- A clean build takes about 50 seconds. An incremental build takes about 1 second.
- Only `jni/` builds in parallel. Each class has its own `javac` call. Parallel calls read class files that other
  calls still write, and the build fails with "class file truncated".
- The jar contains `libatk-wrapper-<arch>.so`. A change in `jni/` needs a new jar, not only a new `.so`.
- `wrapper/Makefile` always builds the native library and packs the jar again. `make` at the root is enough.
- `AtkWrapper.java` is generated from `AtkWrapper.java.in`. Edit the `.in` file only.
- If there is no `Makefile`, the script runs `./autogen.sh` once. The README lists the build dependencies.

## Before you install

If the target jar is in a Git checkout, check its `git status`. If it is already modified, it can hold a build of a
different branch. Tell
the user which branch the new jar comes from, and keep the backup path in your report.

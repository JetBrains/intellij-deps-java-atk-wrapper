# Java ATK Wrapper - JetBrains Fork

This is a JetBrains fork of the Java ATK Wrapper project. It is used in the IntelliJ Platform to integrate Java Swing accessibility with the Linux ATK / AT-SPI stack.

## Overview

Java ATK Wrapper is an implementation of ATK (Accessibility Toolkit) using JNI (Java Native Interface) technology. 
It converts Java Swing events into ATK events and sends these events to ATK-Bridge.

JAW consists of two main components:

- `wrapper/` — Java layer  
  Listens to Swing accessibility events and adapts JAAPI interfaces to ATK concepts.

- `jni/` — Native C layer
  Converts Java-side events into GLib/ATK signals and communicates with ATK-Bridge.

## Documentation

Additional documentation is available in the `docs/` directory:

- [`docs/architecture.md`](docs/architecture.md) — Architecture overview and design details
- [`docs/debugging.md`](docs/debugging.md) — Debugging guide and troubleshooting tips

## Install

### Dependencies (Ubuntu/Debian)
Before building, ensure all required build tools and C libraries are installed on your system. You can install them by running:

```bash
sudo apt install autoconf autoconf-archive libtool libgirepository1.0-dev libatk1.0-dev libatk-bridge2.0-dev libdbus-1-dev
```

### Build and install

Build from source with autotools:

On Oracle Linux 8, install the native build prerequisites first:

```bash
sudo dnf install dnf-plugins-core
sudo dnf config-manager --enable ol8_codeready_builder
sudo dnf install autoconf autoconf-archive automake libtool make gcc gcc-c++ \
  java-devel pkgconf-pkg-config atk-devel at-spi2-atk-devel \
  at-spi2-core-devel dbus-devel glib2-devel gobject-introspection-devel \
  xorg-x11-utils
```

1. Prepare configure scripts:
   - Git checkout: `./autogen.sh [--prefix=PATH] [JAVA_HOME=...] [JDK_SRC=...]`
   - Source tarball: `./configure [--prefix=PATH] [JAVA_HOME=...] [JDK_SRC=...]`
2. Build: `make`
3. Install: `make install`

Example:
```bash
./autogen.sh --prefix=/opt/gnome-2.0 JAVA_HOME=/usr/java
make
make install
```

### Tests

`make check` runs the Java unit tests in `wrapper/test/` with JUnit 5. The tests run headless and without
`libatk-wrapper.so`: they replace the native calls with a recording `NativeBridge`, so they need neither a display
nor an AT-SPI bus. On the first run, the target downloads `junit-platform-console-standalone` from Maven Central
into `test-lib/` and verifies its SHA-256. To use a local copy instead:

```bash
make check JUNIT_CONSOLE_JAR=/path/to/junit-platform-console-standalone-1.14.4.jar
```

`MAVEN_REPOSITORY_URL=<url>` downloads the jar from a Maven mirror instead; the SHA-256 check still applies.
JUnit XML reports go to `test-reports/`, or to `JUNIT_REPORTS_DIR=<dir>`; `make check` and `make clean` remove only
the `TEST-*.xml` files of the tests there. A relative `<dir>` is relative to the top build directory. `configure`
takes these variables from the environment, and `make check` also accepts them on the command line.

The download needs `curl` and `sha256sum` (or `shasum`). `configure --enable-java-tests=auto`, the default, turns the
tests off with a warning when neither they nor `JUNIT_CONSOLE_JAR` are available; `--enable-java-tests=yes` makes this
an error, and `--disable-java-tests` turns the tests off, for example in an offline package build.

`make check` also runs the native unit tests in `jni/tests/` with GLib testing. A test program embeds a headless JVM,
loads `libatk-wrapper.so` into it and checks the ATK objects that the native layer builds for Swing components: their
names, roles, states and parents, as the AT-SPI bridge reads them. The calls between C and Java are real JNI calls;
the AT-SPI bridge is never loaded, so the tests need no display and no AT-SPI bus. They need Linux, ATK and
`libjvm.so`, which `configure` looks for in `JAVA_HOME/lib/server`; `JVM_LIBDIR=<dir>` points it elsewhere.
`--enable-native-tests=auto`, the default, turns the tests off with a warning when one of them is missing, for
example on macOS; `--enable-native-tests=yes` makes this an error, and `--disable-native-tests` turns them off. Each
test program writes `TEST-native-<program>.xml` next to the Java reports. `JAW_TEST_JVM_OPTIONS` adds JVM options to
a run.

The test JVM always runs with `-Xcheck:jni`, so the tests also check how the native layer uses JNI. A message of the
JNI checks is reported in the log of the test that caused it; `make check JAW_TEST_JNI_WARNINGS=fail` makes it fail
that test.

`configure --enable-sanitizers` builds the native library and the test programs with AddressSanitizer and
UndefinedBehaviorSanitizer, and `make check` then fails on a memory error or on undefined behavior in the native
layer. Such a build is for tests only: build it in a separate copy of the sources, and never install or package its
library. Leak detection is off, because the JVM never frees much of its memory.

`make check-atspi` runs the AT-SPI integration tests in `tests/atspi/`, which `make check` does not run. They start a
Swing application with the wrapper as its assistive technology and check it with libatspi, as a screen reader sees
it: that the application and its window reach the AT-SPI desktop, and that window events arrive over the bus. They
cover only what the unit tests cannot: the start of the wrapper in an application, real window and focus events, the
calls between the GLib thread and the EDT, and the delivery over D-Bus. `tests/atspi/run.sh` starts an X server, a
session bus and a window manager for each run and lists the packages it needs; the report is
`TEST-atspi.xml` next to the other reports, and the logs of a run are in `tests/atspi/logs/`.

`make check-orca` runs a trial of tests of what Orca says, `tests/atspi/test_orca.py`. They start Orca next to the
application of the AT-SPI tests, press keys and read the phrases of Orca from its debug log, so they need no audio;
they need the package `orca` in addition. The module that reads the log relies on Orca 3.36 to 50. The report is
`TEST-atspi-orca.xml`, and the logs are in `tests/atspi/logs-orca/`. What these tests do not cover stays a manual
check with a screen reader.

## Upstream Project

This project is based on **Java ATK Wrapper** from the GNOME Project.

- **Original Repository**: https://gitlab.gnome.org/GNOME/java-atk-wrapper
- **Original Homepage**: https://wiki.gnome.org/Accessibility/JavaAtkWrapper
- **Original Bug Database**: https://gitlab.gnome.org/GNOME/java-atk-wrapper/issues/
- **Original Download Page**: https://download.gnome.org/sources/java-atk-wrapper

### Original Authors and Maintainers

- Ke Wang (ke.wang@sun.com)
- Li Yuan (lee.yuan@oracle.com)
- Magdalen Berns (m.berns@thismagpie.com)
- Samuel Thibault (samuel.thibault@ens-lyon.org) - Current upstream maintainer
- Giuseppe Capaldo (giuseppecapaldo93@gmail.com)

Contains inspiration and possibly a little code from java-access-bridge, written by Bill Haneman and Louise Miller.

## JetBrains Fork Notes

This repository is a JetBrains-maintained fork for IntelliJ Platform usage.

Notable fork-specific changes are documented in: [`docs/fork-notes.md`](docs/fork-notes.md).

## License

This project is licensed under the
**GNU Lesser General Public License v2.1 (LGPL-2.1)**.

See [COPYING.LESSER](COPYING.LESSER) for the full license text.

## Contributing

This is an internal JetBrains fork.

For upstream contributions, please visit:
https://gitlab.gnome.org/GNOME/java-atk-wrapper

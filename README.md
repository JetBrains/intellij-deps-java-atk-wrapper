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

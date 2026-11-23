#! /bin/sh
# Runs the AT-SPI integration tests: `make check-atspi` calls it.
#
# The tests need what a desktop session gives an application: an X server, a session bus with the AT-SPI bus
# behind it, and a window manager, so that the window of the test application gets the focus. This script
# starts them for one run, on a display and a bus of its own, and then starts pytest.
#
# Environment:
#   JAW_JAR             java-atk-wrapper.jar with the bundled native library (required)
#   JAVA, JAVAC         default: java, javac
#   JUNIT_REPORTS_DIR   where TEST-atspi.xml goes, default: test-reports in the working directory
#   ATSPI_BUILD_DIR     where the test application is compiled, default: tests/atspi/build
#   ATSPI_LOG_DIR       the logs of a run, default: tests/atspi/logs
#   ATSPI_TESTS         the test file to run, default: test_atspi.py. `make check-orca` runs test_orca.py,
#                       which also needs the package orca.
#   JAW_DEBUG           passed on to the wrapper, which then writes jaw_log.txt to ATSPI_LOG_DIR
#   JUNIT_REPORT_SUFFIX     added to the report name, for example "-asan" gives TEST-atspi-asan.xml
#   ATSPI_SANITIZER_PRELOAD the AddressSanitizer runtime to preload into the application, for a library
#                           built with configure --enable-sanitizers
#
# Packages on Ubuntu: xvfb xauth dbus at-spi2-core gir1.2-atspi-2.0 python3-gi python3-pytest openbox x11-utils.

set -eu

here=$(cd "$(dirname "$0")" && pwd)

# The second pass, inside the X server and the session bus that the first pass started.
if test "${1:-}" = "--inside-session"; then
    openbox >"$ATSPI_LOG_DIR/openbox.log" 2>&1 &
    window_manager=$!
    trap 'kill "$window_manager" 2>/dev/null || :' EXIT

    # Wait until the window manager owns the display: before that a new window may not become active.
    attempts=0
    until xprop -root _NET_SUPPORTING_WM_CHECK 2>/dev/null | grep -q 'window id'; do
        attempts=$((attempts + 1))
        if test "$attempts" -ge 100; then
            echo "$0: the window manager did not start, see $ATSPI_LOG_DIR/openbox.log" >&2
            exit 1
        fi
        sleep 0.1
    done

    # Start the AT-SPI bus and mark accessibility as enabled, as a session with a screen reader does.
    dbus-send --session --print-reply --dest=org.a11y.Bus /org/a11y/bus \
        org.freedesktop.DBus.Properties.Set \
        string:org.a11y.Status string:IsEnabled variant:boolean:true \
        >"$ATSPI_LOG_DIR/a11y-bus.log" 2>&1 ||
        echo "$0: warning: could not set org.a11y.Status.IsEnabled, see $ATSPI_LOG_DIR/a11y-bus.log" >&2

    # A hung application must fail the run, not block the build.
    status=0
    timeout 300 python3 -m pytest "$here/$ATSPI_TESTS" -v -p no:cacheprovider \
        --junitxml="$JUNIT_REPORTS_DIR/TEST-atspi${JUNIT_REPORT_SUFFIX:-}.xml" || status=$?
    exit "$status"
fi

: "${JAW_JAR:?set JAW_JAR to java-atk-wrapper.jar, or run make check-atspi}"
JAVA=${JAVA:-java}
JAVAC=${JAVAC:-javac}
ATSPI_BUILD_DIR=${ATSPI_BUILD_DIR:-$here/build}
ATSPI_LOG_DIR=${ATSPI_LOG_DIR:-$here/logs}
JUNIT_REPORTS_DIR=${JUNIT_REPORTS_DIR:-$PWD/test-reports}
ATSPI_TESTS=${ATSPI_TESTS:-test_atspi.py}
test -f "$here/$ATSPI_TESTS" || { echo "$0: no test file $here/$ATSPI_TESTS" >&2; exit 1; }

for tool in xvfb-run dbus-run-session dbus-send xprop openbox python3 timeout; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "$0: $tool not found; the header of this script lists the packages" >&2
        exit 1
    fi
done
test -f "$JAW_JAR" || { echo "$0: $JAW_JAR not found; run make first" >&2; exit 1; }

rm -rf "$ATSPI_BUILD_DIR" "$ATSPI_LOG_DIR"
mkdir -p "$ATSPI_BUILD_DIR/classes" "$ATSPI_LOG_DIR" "$JUNIT_REPORTS_DIR"
# The pytest and the application run in other directories, so the paths must be absolute.
ATSPI_LOG_DIR=$(cd "$ATSPI_LOG_DIR" && pwd)
JUNIT_REPORTS_DIR=$(cd "$JUNIT_REPORTS_DIR" && pwd)
rm -f "$JUNIT_REPORTS_DIR/TEST-atspi${JUNIT_REPORT_SUFFIX:-}.xml"

"$JAVAC" -d "$ATSPI_BUILD_DIR/classes" "$here/app/AtspiTestApp.java"

ATSPI_JAVA=$JAVA
ATSPI_CLASSPATH=$JAW_JAR:$(cd "$ATSPI_BUILD_DIR/classes" && pwd)
# The messages of g_debug in the native layer go to the output of the application.
G_MESSAGES_DEBUG=${G_MESSAGES_DEBUG:-all}
# No .pyc files in the source tree.
PYTHONDONTWRITEBYTECODE=1
ATSPI_SANITIZER_PRELOAD=${ATSPI_SANITIZER_PRELOAD:-}
export ATSPI_JAVA ATSPI_CLASSPATH ATSPI_LOG_DIR JUNIT_REPORTS_DIR G_MESSAGES_DEBUG PYTHONDONTWRITEBYTECODE
export ATSPI_SANITIZER_PRELOAD ATSPI_TESTS

# Xvfb is the outer one, so that DISPLAY is set when the session bus starts the AT-SPI bus.
exec xvfb-run -a -e "$ATSPI_LOG_DIR/xvfb.log" -s "-screen 0 1280x1024x24" \
    dbus-run-session -- /bin/sh "$0" --inside-session

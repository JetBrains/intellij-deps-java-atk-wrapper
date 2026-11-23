SUBDIRS=wrapper jni

EXTRA_DIST = JavaInfo.java HACKING README.md COPYING.LESSER wrapper/test tests/atspi

ACLOCAL_AMFLAGS=-I m4 ${ACLOCAL_FLAGS}

# Java unit tests, run by `make check`.
#
# The tests compile the Java sources again into their own directory. The class path holds no
# libatk-wrapper.so, so the static initializer of AtkWrapper does not load native code, and the
# tests replace the native calls with a RecordingNativeBridge. configure takes JUNIT_CONSOLE_JAR
# (a local copy of the JUnit launcher instead of downloading it), MAVEN_REPOSITORY_URL (a mirror
# to download it from) and JUNIT_REPORTS_DIR (where the XML reports go) from the environment;
# they can also be passed to make. --disable-java-tests turns the tests off, for example in an
# offline build.
JUNIT_VERSION = 1.14.4
JUNIT_SHA256 = 7c6968cbcaf4301c729f202b23b7d736c5d88be625fc7d27ad5d746146a8bc28
JUNIT_JAR_NAME = junit-platform-console-standalone-$(JUNIT_VERSION).jar
JUNIT_URL = $(MAVEN_REPOSITORY_URL)/org/junit/platform/junit-platform-console-standalone/$(JUNIT_VERSION)/$(JUNIT_JAR_NAME)
JUNIT_DOWNLOAD_DIR = $(top_builddir)/test-lib

TEST_CLASSES_DIR = $(top_builddir)/test-classes
JAW_MAIN_SOURCES = \
	$(filter-out %/AtkWrapper.java, $(wildcard $(top_srcdir)/wrapper/org/GNOME/Accessibility/*.java)) \
	$(top_builddir)/wrapper/org/GNOME/Accessibility/AtkWrapper.java
JAW_TEST_SOURCES = $(wildcard $(top_srcdir)/wrapper/test/org/GNOME/Accessibility/*.java)

$(JUNIT_DOWNLOAD_DIR)/$(JUNIT_JAR_NAME):
	@if test -z '$(CURL)' || test -z '$(SHA256SUM)'; then \
		echo "Downloading JUnit needs curl and sha256sum. Pass JUNIT_CONSOLE_JAR=<path> instead." >&2; \
		exit 1; \
	fi
	$(MKDIR_P) $(JUNIT_DOWNLOAD_DIR)
	$(CURL) -fsSL -o $@.tmp '$(JUNIT_URL)'
	echo '$(JUNIT_SHA256)  $@.tmp' | $(SHA256SUM) -c -
	mv $@.tmp $@

# JUNIT_REPORTS_DIR may be a directory with other files, so only the reports JUnit writes are removed.
# check-local runs after the subdirectories, so it must not remove the TEST-native-*.xml reports
# that jni/tests wrote before; the JUnit Platform names its reports TEST-junit-*.xml.
if JAVA_TESTS
check-local: $(JUNIT_CONSOLE_JAR)
	rm -rf $(TEST_CLASSES_DIR)
	rm -f $(JUNIT_REPORTS_DIR)/TEST-junit-*.xml
	$(MKDIR_P) $(TEST_CLASSES_DIR)
	$(JAVAC) $(JAVACFLAGS) -cp $(JUNIT_CONSOLE_JAR) -d $(TEST_CLASSES_DIR) $(JAW_MAIN_SOURCES) $(JAW_TEST_SOURCES)
	$(JAVA) -Djava.awt.headless=true -jar $(JUNIT_CONSOLE_JAR) execute \
		--disable-banner --disable-ansi-colors --fail-if-no-tests \
		--reports-dir $(JUNIT_REPORTS_DIR) \
		--class-path $(TEST_CLASSES_DIR) --scan-class-path $(TEST_CLASSES_DIR)
else
check-local:
	@echo "Skipping the Java unit tests: they are disabled by configure, see --enable-java-tests."
endif

# AT-SPI integration tests, run by `make check-atspi`, not by `make check`: they start a Swing
# application with the wrapper and look at it through the AT-SPI bus, so they need an X server, a
# session bus and Python with libatspi. tests/atspi/run.sh starts what a run needs and lists the
# packages. The report goes to JUNIT_REPORTS_DIR, next to the reports of the unit tests.
# After configure --enable-sanitizers, the application runs with the AddressSanitizer runtime
# preloaded, because its JVM is not built with it.
ATSPI_TESTS_DIR = $(abs_top_builddir)/tests/atspi

check-atspi: all
	JAVA='$(JAVA)' JAVAC='$(JAVAC)' \
	JAW_JAR='$(abs_top_builddir)/wrapper/$(JAW_JAR_NAME)' \
	JUNIT_REPORTS_DIR='$(JUNIT_REPORTS_DIR)' \
	ATSPI_BUILD_DIR='$(ATSPI_TESTS_DIR)/build' \
	ATSPI_LOG_DIR='$(ATSPI_TESTS_DIR)/logs' \
	ATSPI_SANITIZER_PRELOAD='$(ASAN_RUNTIME)' \
	$(SHELL) $(top_srcdir)/tests/atspi/run.sh

# A trial: tests of what Orca says, run by `make check-orca` only. They start Orca next to the
# application of the AT-SPI tests and read its speech from its debug log, see
# tests/atspi/orca_speech.py. They need what check-atspi needs and the package orca. The logs go
# to a directory of their own, because run.sh empties its log directory first. The report name
# keeps a JUNIT_REPORT_SUFFIX of the caller behind "-orca".
check-orca: all
	JAVA='$(JAVA)' JAVAC='$(JAVAC)' \
	JAW_JAR='$(abs_top_builddir)/wrapper/$(JAW_JAR_NAME)' \
	JUNIT_REPORTS_DIR='$(JUNIT_REPORTS_DIR)' \
	ATSPI_BUILD_DIR='$(ATSPI_TESTS_DIR)/build' \
	ATSPI_LOG_DIR='$(ATSPI_TESTS_DIR)/logs-orca' \
	ATSPI_SANITIZER_PRELOAD='$(ASAN_RUNTIME)' \
	ATSPI_TESTS=test_orca.py JUNIT_REPORT_SUFFIX='-orca$(JUNIT_REPORT_SUFFIX)' \
	$(SHELL) $(top_srcdir)/tests/atspi/run.sh

.PHONY: check-atspi check-orca

clean-local:
	$(RM) *.class
	rm -rf $(TEST_CLASSES_DIR)
	rm -rf $(ATSPI_TESTS_DIR)/build $(ATSPI_TESTS_DIR)/logs $(ATSPI_TESTS_DIR)/logs-orca
	rm -f $(JUNIT_REPORTS_DIR)/TEST-*.xml
	rmdir $(JUNIT_REPORTS_DIR) 2>/dev/null || :

distclean-local:
	rm -rf $(JUNIT_DOWNLOAD_DIR)

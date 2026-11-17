SUBDIRS=wrapper jni

EXTRA_DIST = JavaInfo.java HACKING README.md COPYING.LESSER wrapper/test

ACLOCAL_AMFLAGS=-I m4 ${ACLOCAL_FLAGS}

# Java unit tests, run by `make check`.
#
# The tests compile the Java sources again into their own directory. The class path holds no
# libatk-wrapper.so, so the static initializer of AtkWrapper does not load native code, and the
# tests replace the native calls with a RecordingNativeBridge. configure takes JUNIT_CONSOLE_JAR
# (a local copy of the JUnit launcher instead of downloading it) from the environment; it can
# also be passed to make. --disable-java-tests turns the tests off, for example in an offline
# build.
JUNIT_VERSION = 1.14.4
JUNIT_SHA256 = 7c6968cbcaf4301c729f202b23b7d736c5d88be625fc7d27ad5d746146a8bc28
JUNIT_JAR_NAME = junit-platform-console-standalone-$(JUNIT_VERSION).jar
JUNIT_URL = https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$(JUNIT_VERSION)/$(JUNIT_JAR_NAME)
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

if JAVA_TESTS
check-local: $(JUNIT_CONSOLE_JAR)
	rm -rf $(TEST_CLASSES_DIR)
	$(MKDIR_P) $(TEST_CLASSES_DIR)
	$(JAVAC) $(JAVACFLAGS) -cp $(JUNIT_CONSOLE_JAR) -d $(TEST_CLASSES_DIR) $(JAW_MAIN_SOURCES) $(JAW_TEST_SOURCES)
	$(JAVA) -Djava.awt.headless=true -jar $(JUNIT_CONSOLE_JAR) execute \
		--disable-banner --disable-ansi-colors --fail-if-no-tests \
		--class-path $(TEST_CLASSES_DIR) --scan-class-path $(TEST_CLASSES_DIR)
else
check-local:
	@echo "Skipping the Java unit tests: they are disabled by configure, see --enable-java-tests."
endif

clean-local:
	$(RM) *.class
	rm -rf $(TEST_CLASSES_DIR)

distclean-local:
	rm -rf $(JUNIT_DOWNLOAD_DIR)

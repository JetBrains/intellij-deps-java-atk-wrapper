# Native unit tests, run by `make check` on Linux.
#
# Each test program embeds a JVM, loads libatk-wrapper.so into it and checks the ATK objects
# that the native layer builds for Swing components, see jaw-test-jvm.h. The Java layer and the
# fixtures in java/ are compiled into classes/ here: the wrapper/ build directory holds the
# bundled native library as a class path resource, and the static initializer of AtkWrapper
# would load a second copy of it from there. configure --disable-native-tests turns the tests
# off.

EXTRA_DIST = \
	java/org/GNOME/Accessibility/NativeTestFixtures.java \
	junit-tap-driver.sh \
	tap-to-junit.awk

if NATIVE_TESTS

check_PROGRAMS = test-jaw-object
TESTS = $(check_PROGRAMS)
check_DATA = classes.stamp

AM_CPPFLAGS = \
	-I$(top_srcdir)/jni/src \
	-I$(top_builddir)/jni/src \
	-I$(top_builddir) \
	$(JNI_INCLUDES) \
	$(JAW_CFLAGS) \
	$(PLATFORM_CFLAGS)

LDADD = \
	$(top_builddir)/jni/src/libatk-wrapper.la \
	$(JAW_LIBS) \
	-L$(JVM_LIBDIR) -ljvm -Wl,-rpath,$(JVM_LIBDIR)

test_jaw_object_SOURCES = test-jaw-object.c jaw-test-jvm.c jaw-test-jvm.h

# The programs print TAP; the driver also writes a JUnit XML report for each of them.
LOG_DRIVER = $(SHELL) $(srcdir)/junit-tap-driver.sh
AM_LOG_FLAGS = --tap
AM_TESTS_ENVIRONMENT = \
	JAW_TEST_CLASSPATH='$(abs_builddir)/classes'; \
	JAW_TEST_LIBRARY='$(abs_top_builddir)/jni/src/.libs/libatk-wrapper.so'; \
	TAP_DRIVER='$(top_srcdir)/config/tap-driver.sh'; \
	TAP_TO_JUNIT='$(srcdir)/tap-to-junit.awk'; \
	JUNIT_REPORTS_DIR='$(JUNIT_REPORTS_DIR)'; \
	AM_TAP_AWK='$(AWK)'; \
	AWK='$(AWK)'; \
	export JAW_TEST_CLASSPATH JAW_TEST_LIBRARY TAP_DRIVER TAP_TO_JUNIT JUNIT_REPORTS_DIR AM_TAP_AWK AWK;

JAW_JAVA_SOURCES = \
	$(filter-out %/AtkWrapper.java, $(wildcard $(top_srcdir)/wrapper/org/GNOME/Accessibility/*.java)) \
	$(top_builddir)/wrapper/org/GNOME/Accessibility/AtkWrapper.java \
	$(srcdir)/java/org/GNOME/Accessibility/NativeTestFixtures.java

classes.stamp: $(JAW_JAVA_SOURCES)
	rm -rf classes
	$(MKDIR_P) classes
	$(JAVAC) $(JAVACFLAGS) -d classes $(JAW_JAVA_SOURCES)
	touch $@

endif

# JUNIT_REPORTS_DIR may be a directory with other files, so only the reports of these tests are removed.
clean-local:
	rm -rf classes classes.stamp
	rm -f $(JUNIT_REPORTS_DIR)/TEST-native-*.xml

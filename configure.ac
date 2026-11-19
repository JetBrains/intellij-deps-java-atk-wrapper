AC_PREREQ(2.63)
AC_INIT([java-atk-wrapper],[0.44.0],[https://gitlab.gnome.org/GNOME/java-atk-wrapper/issues/new])
: ${CXXFLAGS=""}

AC_CONFIG_AUX_DIR(config)
dnl The native unit tests run through the TAP driver of automake; automake --add-missing installs it.
AC_REQUIRE_AUX_FILE([tap-driver.sh])
AM_INIT_AUTOMAKE([1.11 -Wall foreign])

AM_SILENT_RULES([yes])
m4_pattern_allow([AM_PROG_AR])
m4_ifdef([AM_PROG_AR], [AM_PROG_AR])

# jni
AC_CONFIG_HEADERS([config.h])

AC_PROG_CC(gcc)
AC_PROG_CXX
AC_PROG_INSTALL

# Initialize libtool
LT_PREREQ([2.2.6])
LT_INIT([disable-static])

AC_CONFIG_MACRO_DIR([m4])

m4_define([req_ver_atk], [2.28.0])
m4_define([req_ver_atk_bridge], [2.26.0])
m4_define([req_ver_atspi], [2.33.1])
m4_define([req_ver_glib], [2.56.0])

PKG_CHECK_MODULES(JAW, [
  atk >= req_ver_atk
  atk-bridge-2.0 >= req_ver_atk_bridge
  atspi-2 >= req_ver_atspi
  dbus-1
  glib-2.0 >= req_ver_glib
  gmodule-2.0 >= req_ver_glib
  gthread-2.0 >= req_ver_glib
  gobject-2.0 >= req_ver_glib
])

LIB_ATK_WRAPPER_LD_FLAGS="-avoid-version -no-undefined -Wl,-z,now"
AC_SUBST(LIB_ATK_WRAPPER_LD_FLAGS)

AC_ARG_ENABLE([modular-jar],
            [AS_HELP_STRING([--enable-modular-jar],
            [Build modular java-atk-wrapper jar])],
            [enable_modular_jar="$enableval"],
            [enable_modular_jar=no])
AS_IF([test "x$enable_modular_jar" = "xyes"], [
  JAVA_REQUIRED=9.0.0
], [
  JAVA_REQUIRED=1.7.0
])
JAVA_ERROR_MESSAGE="Java $JAVA_REQUIRED or later is required to build java-access-bridge"
AM_CONDITIONAL([MODULAR_JAR], [test "x$enable_modular_jar" = "xyes"])

# java wrapper

AM_CONDITIONAL(USER, test `whoami` = "root")

AC_ARG_VAR([JAVA_HOME],[Java Runtime Environment location])
AC_ARG_VAR([JDK_SRC],[JDK installation path])

CUR_DIR=`pwd`
SRC_DIR=`dirname $0`

AC_ARG_WITH([jdk_auto_detect],
            [AS_HELP_STRING([--with-jdk-auto-detect],
            [Auto-detection of jdk location])],
            [],
            [with_jdk_auto_detect=yes])
if test "x$with_jdk_auto_detect" = "xyes"; then
  `javac -nowarn $JAVACFLAGS ${srcdir}/JavaInfo.java -d .`
  JDK_SRC=`java JavaInfo`
  JAVA_HOME="${JDK_SRC}/jre"
  if test ! -d ${JAVA_HOME}; then
    JAVA_HOME="${JDK_SRC}"
  fi
else
  if test -z "$JDK_SRC"; then
    AC_MSG_ERROR("You should specify JDK_SRC")
  else
    if test -z "$JAVA_HOME"; then
      JAVA_HOME="${JDK_SRC}/jre"
      if test ! -d ${JAVA_HOME}; then
        JAVA_HOME="${JDK_SRC}"
      fi
    fi
  fi
fi

AC_SUBST(JDK_SRC)
AC_SUBST(JAVA_HOME)

if test -z "$JAVA_HOME"; then
  JAVA_PATH=${PATH}
else
  JAVA_PATH=${JDK_SRC}:${JDK_SRC}/bin:${PATH}
fi

AC_PATH_PROG(JAVA,java,,${JAVA_PATH})
if test -z "$JAVA"; then
  AC_MSG_ERROR([$JAVA_ERROR_MESSAGE])
fi
AC_SUBST(JAVA)

AC_PATH_PROG(JAVAC,javac,,${JAVA_PATH})
if test -z "$JAVAC"; then
  AC_MSG_ERROR([$JAVA_ERROR_MESSAGE])
fi
AC_SUBST(JAVAC)

AC_SUBST(JAVACFLAGS)

AC_PATH_PROG(JAR,jar,,${JAVA_PATH})
if test -z "$JAR"; then
  AC_MSG_ERROR([$JAVA_ERROR_MESSAGE])
fi
AC_SUBST(JAR)

dnl Java unit tests, run by make check. The variables are precious: configure takes them from the environment,
dnl and make check also accepts them on the command line.
AC_ARG_VAR([JUNIT_CONSOLE_JAR], [junit-platform-console-standalone jar for make check, downloaded when not set])
AC_ARG_VAR([MAVEN_REPOSITORY_URL], [Maven repository that make check downloads JUnit from])
AC_ARG_VAR([JUNIT_REPORTS_DIR], [directory for the JUnit XML reports of make check])
AC_ARG_VAR([CURL], [curl command that downloads JUnit])
AC_ARG_VAR([SHA256SUM], [command that verifies the JUnit download, called like sha256sum -c])
AC_ARG_ENABLE([java-tests],
  [AS_HELP_STRING([--enable-java-tests=@<:@yes/no/auto@:>@],
    [run the Java unit tests in make check; auto runs them when JUNIT_CONSOLE_JAR is set or curl and sha256sum are found @<:@default=auto@:>@])],
  [], [enable_java_tests=auto])
AC_PATH_PROG([CURL], [curl])
AC_PATH_PROGS([SHA256SUM], [sha256sum gsha256sum])
if test -z "$SHA256SUM"; then
  AC_PATH_PROG([SHASUM], [shasum])
  if test -n "$SHASUM"; then
    SHA256SUM="$SHASUM -a 256"
  fi
fi
if test -n "$JUNIT_CONSOLE_JAR" || { test -n "$CURL" && test -n "$SHA256SUM"; }; then
  junit_available=yes
else
  junit_available=no
fi
case "$enable_java_tests" in
  yes)
    if test "$junit_available" = no; then
      AC_MSG_ERROR([--enable-java-tests needs JUNIT_CONSOLE_JAR, or curl and sha256sum to download JUnit])
    fi
    ;;
  no)
    ;;
  auto)
    enable_java_tests=$junit_available
    if test "$enable_java_tests" = no; then
      AC_MSG_WARN([make check skips the Java unit tests: set JUNIT_CONSOLE_JAR, or install curl and sha256sum])
    fi
    ;;
  *)
    AC_MSG_ERROR([bad value $enable_java_tests for --enable-java-tests])
    ;;
esac
AM_CONDITIONAL(JAVA_TESTS, test "$enable_java_tests" = yes)
: ${JUNIT_CONSOLE_JAR='$(JUNIT_DOWNLOAD_DIR)/$(JUNIT_JAR_NAME)'}
: ${MAVEN_REPOSITORY_URL=https://repo1.maven.org/maven2}
: ${JUNIT_REPORTS_DIR='$(top_builddir)/test-reports'}


if test "x$GCC" = xyes; then
  JAW_CFLAGS="$JAW_CFLAGS $CFLAGS"
else
  AC_MSG_ERROR("You should compile with GCC")
fi
AC_SUBST(JAW_CFLAGS)

AX_IS_RELEASE(dash-version)
AX_COMPILER_FLAGS()
case "$WARN_CFLAGS" in
    *-Werror*)
        WARN_CFLAGS="$WARN_CFLAGS -Wno-error=deprecated-declarations"
        WARN_CFLAGS="$WARN_CFLAGS -Wno-error=strict-prototypes"
        WARN_CFLAGS="$WARN_CFLAGS -Wno-error=declaration-after-statement"
        ;;
esac
AM_CFLAGS="$AM_CFLAGS $WARN_CFLAGS -Wno-declaration-after-statement"
AC_SUBST(AM_CFLAGS)

CHECK_JNI

AC_MSG_CHECKING([JDK version])
jversion=`$JAVA -version 2>&1 | head -n1 | cut -f2 -d'"' | cut -f1 -d_`
AC_MSG_RESULT($jversion)
j_major_version=0`echo $jversion | cut -f1 -d.`
j_minor_version=0`echo $jversion | cut -f2 -d.`
j_micro_version=0`echo $jversion | cut -f3 -d.`
min_major_version=0`echo $JAVA_REQUIRED | cut -f1 -d.`
min_minor_version=0`echo $JAVA_REQUIRED | cut -f2 -d.`
min_micro_version=0`echo $JAVA_REQUIRED | cut -f3 -d.`
if test "$j_major_version" -lt "$min_major_version"; then
  AC_MSG_ERROR([$JAVA_ERROR_MESSAGE])
elif test "$j_major_version" = "$min_major_version"; then
  if test "$j_minor_version" -lt "$min_minor_version"; then
    AC_MSG_ERROR([$JAVA_ERROR_MESSAGE])
  elif test "$j_minor_version" = "$min_minor_version"; then
    if test "$j_micro_version" -lt "$min_micro_version"; then
      AC_MSG_ERROR([$JAVA_ERROR_MESSAGE])
    fi
  fi
fi

dnl Native unit tests, run by make check. A test program embeds a JVM, so it links libjvm.so, and it
dnl needs ATK, which only Linux has.
AC_ARG_VAR([JVM_LIBDIR], [directory with libjvm.so for the native unit tests, found in JAVA_HOME when not set])
AC_ARG_ENABLE([native-tests],
  [AS_HELP_STRING([--enable-native-tests=@<:@yes/no/auto@:>@],
    [run the native unit tests in make check; auto runs them on Linux with libjvm.so @<:@default=auto@:>@])],
  [], [enable_native_tests=auto])
if test -z "$JVM_LIBDIR"; then
  for jvm_dir in "$JAVA_HOME/lib/server" "$JDK_SRC/lib/server"; do
    if test -f "$jvm_dir/libjvm.so"; then
      JVM_LIBDIR=$jvm_dir
      break
    fi
  done
fi
AC_MSG_CHECKING([for libjvm.so])
AS_IF([test -n "$JVM_LIBDIR" && test -f "$JVM_LIBDIR/libjvm.so"],
  [AC_MSG_RESULT([$JVM_LIBDIR]); jvm_available=yes],
  [AC_MSG_RESULT([no]); jvm_available=no])
case "$host_os" in
  linux*) native_tests_available=$jvm_available ;;
  *) native_tests_available=no ;;
esac
case "$enable_native_tests" in
  yes)
    if test "$native_tests_available" = no; then
      AC_MSG_ERROR([--enable-native-tests needs Linux and libjvm.so; set JVM_LIBDIR to the directory of libjvm.so])
    fi
    ;;
  no)
    ;;
  auto)
    enable_native_tests=$native_tests_available
    if test "$enable_native_tests" = no; then
      AC_MSG_WARN([make check skips the native unit tests: they need Linux and libjvm.so, see JVM_LIBDIR])
    fi
    ;;
  *)
    AC_MSG_ERROR([bad value $enable_native_tests for --enable-native-tests])
    ;;
esac
AM_CONDITIONAL(NATIVE_TESTS, test "$enable_native_tests" = yes)

XPROP_PATH=${PATH}:/usr/X11/bin
AC_PATH_PROG(XPROP,xprop,,${XPROP_PATH})
if test -z "$XPROP"; then
  AC_MSG_ERROR([No xprop found])
fi
AC_SUBST(XPROP)

AC_ARG_WITH(jardir, AS_HELP_STRING([--with-jardir=[dirname]],[Directory for installing the java-atk-wrapper.jar file]))
if test -z "$with_jardir" ; then
    if test -d ${JAVA_HOME}/lib/ext ; then
        if test "x$(basename ${JAVA_HOME})" = xjre; then
            JARDIR=${libdir}/jvm/$(basename ${JDK_SRC})/jre/lib/ext
        else
            JARDIR=${libdir}/jvm/$(basename ${JAVA_HOME})/lib/ext
        fi
    else
        JARDIR=${libdir}/jvm/$(basename ${JAVA_HOME})/lib
    fi
else
    JARDIR=$with_jardir
fi
AC_SUBST(JARDIR)

AC_ARG_WITH(propertiesdir, AS_HELP_STRING([--with-propertiesdir=[dirname]],[Directory for installing the accessibility.properties file]))
if test -z "$with_propertiesdir" ; then
    if test -d ${JAVA_HOME}/conf ; then
        PROPERTIESDIR=${libdir}/jvm/$(basename ${JAVA_HOME})/conf
    else
        if test "x$(basename ${JAVA_HOME})" = xjre; then
            PROPERTIESDIR=${libdir}/jvm/$(basename ${JDK_SRC})/jre/lib
        else
            PROPERTIESDIR=${libdir}/jvm/$(basename ${JAVA_HOME})/lib
        fi
    fi
else
    PROPERTIESDIR=$with_propertiesdir
fi
AC_SUBST(PROPERTIESDIR)

JAW_JAR_NAME=java-atk-wrapper.jar
AC_SUBST(JAW_JAR_NAME)

BUNDLED_NATIVE_LIBRARY_NAME=libatk-wrapper.so
case "$host_os" in
  linux*)
    case "$host_cpu" in
      aarch64)
        BUNDLED_NATIVE_LIBRARY_NAME=libatk-wrapper-aarch64.so
        ;;
      x86_64)
        BUNDLED_NATIVE_LIBRARY_NAME=libatk-wrapper-x86_64.so
        ;;
    esac
    ;;
esac

AC_SUBST(BUNDLED_NATIVE_LIBRARY_NAME)

AC_CONFIG_FILES([
    Makefile
    jni/Makefile
    jni/src/Makefile
    jni/tests/Makefile
    wrapper/Makefile
    wrapper/org/Makefile
    wrapper/org/GNOME/Makefile
    wrapper/org/GNOME/Accessibility/AtkWrapper.java
    wrapper/org/GNOME/Accessibility/Makefile
    wrapper/META-INF/Makefile
    wrapper/META-INF/services/Makefile
])
AC_OUTPUT

echo "
Build configuration:

       Prefix:                                 ${prefix}
       Source code location:                   ${srcdir}
       Modular jar:                            ${enable_modular_jar}
       Java unit tests:                        ${enable_java_tests}
       Native unit tests:                      ${enable_native_tests}
       Compiler:                               ${CC}
       JDK src:                                ${JDK_SRC}
       JAVA home:                              ${JAVA_HOME}
       Host OS:                                ${host_os}
       Host CPU:                               ${host_cpu}
       Native library resource:                ${BUNDLED_NATIVE_LIBRARY_NAME}
       JNI includes:                           ${JNI_INCLUDES}
       Warn flags:                             ${WARN_CFLAGS}
"

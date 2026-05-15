SUBDIRS=org

if MODULAR_JAR
  SUBDIRS += META-INF
endif

JARFILES=@JAW_JAR_NAME@
ALL_CLASSES=org/GNOME/Accessibility/*.class
DEP_CLASSES=$(wildcard $(ALL_CLASSES))
NATIVE_LIBRARY_NAME=libatk-wrapper.so
JNI_LIBRARY=$(top_builddir)/jni/src/.libs/$(NATIVE_LIBRARY_NAME)
BUNDLED_NATIVE_LIBRARY_NAME=@BUNDLED_NATIVE_LIBRARY_NAME@
BUNDLED_NATIVE_LIBRARY=$(builddir)/$(BUNDLED_NATIVE_LIBRARY_NAME)

java_atk_wrapperdir = $(JARDIR)
propertiesdir = $(PROPERTIESDIR)

java_atk_wrapper_DATA = $(JARFILES)
properties_DATA = accessibility.properties
EXTRA_DIST = $(properties_DATA) \
	     manifest.txt module-info.java

FORCE:

$(BUNDLED_NATIVE_LIBRARY): FORCE
	$(MAKE) -C $(top_builddir)/jni/src libatk-wrapper.la
	cp $(JNI_LIBRARY) "$@"

if MODULAR_JAR

module-info.class : $(srcdir)/module-info.java
	$(JAVAC) -cp $(top_builddir)/wrapper -sourcepath $(top_srcdir)/wrapper:$(top_builddir)/wrapper $(JAVACFLAGS) -d $(top_builddir)/wrapper $<

$(JARFILES) : $(DEP_CLASSES) module-info.class $(BUNDLED_NATIVE_LIBRARY)
	$(JAR) cfm $(JARFILES) $(srcdir)/manifest.txt org/GNOME/Accessibility/*.class $(BUNDLED_NATIVE_LIBRARY_NAME) module-info.class -C $(srcdir) META-INF/services/javax.accessibility.AccessibilityProvider

else

$(JARFILES) : $(DEP_CLASSES) $(BUNDLED_NATIVE_LIBRARY)
	$(JAR) cfm $(JARFILES) $(srcdir)/manifest.txt org/GNOME/Accessibility/*.class $(BUNDLED_NATIVE_LIBRARY_NAME)

endif

install-data-local : all-local
	$(mkinstalldirs) $(DESTDIR)$(propertiesdir) $(DESTDIR)$(java_atk_wrapperdir)
	$(INSTALL_DATA) $(srcdir)/$(properties_DATA) $(DESTDIR)$(propertiesdir)/
	$(INSTALL_DATA) $(java_atk_wrapper_DATA) $(DESTDIR)$(java_atk_wrapperdir)/

all-local : $(DATA) $(JARFILES)

clean-local:
	$(RM) *.class
	$(RM) $(BUNDLED_NATIVE_LIBRARY)
	rm -f $(JARFILES)

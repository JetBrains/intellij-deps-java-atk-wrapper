# Architecture Overview

## Accessibility Stack Context

Java ATK Wrapper operates within the broader AT-SPI accessibility stack, 
which enables communication between applications and assistive technologies such as screen readers.

In our case, the interaction flow is:

```
Swing (Application)
↓
Java ATK Wrapper (ATK implementation)
↓
AT-SPI Bridge (D-Bus)
↓
Orca (Assistive Technology)
```

Java ATK Wrapper exposes Swing accessibility objects through the ATK interfaces,
which are then bridged to the AT-SPI D-Bus infrastructure, allowing assistive technologies such as Orca to query and interact with the UI.

For a detailed description of the AT-SPI architecture, see:
https://gnome.pages.gitlab.gnome.org/at-spi2-core/devel-docs/architecture.html

## Entry Points

We have two entry points into the Java ATK Wrapper code:

#### Entry Point 1. Events from the application

Events from the application come through the class specified in java's `assistive_technologies` variable, set to `org.GNOME.Accessibility.AtkWrapper`. 

For example, a call such as `AtkWrapper::windowActivated` in
`wrapper/org/GNOME/Accessibility/AtkWrapper.java` invokes the corresponding JNI function:
`Java_org_GNOME_Accessibility_AtkWrapper_windowActivate` in `AtkWrapper.c`.

The native layer emits a GLib signal, which is propagated through ATK and forwarded to the AT-SPI bus.

#### Entry Point 2. Requests from the AT-SPI Bus

Requests from assistive technologies arrive via ATK.

For example, `jaw_object_get_name` invokes the Java method `AtkObject::getAccessibleName`, which ultimately calls `AccessibleContext::getAccessibleName()` in the Swing application.

## Threading Model

Java ATK Wrapper runs on two threads and passes work between them:

### Java application threads (including AWT/EDT)

Java-side accessibility events are captured in `AtkWrapper` (`wrapper/org/GNOME/Accessibility/AtkWrapper.java.in`) via
`propertyChange(...)` and `eventDispatched(...)`.
These callbacks call native methods such as `emitSignal(...)`, `objectStateChange(...)`, and window/component event methods,
which enter JNI as `Java_org_GNOME_Accessibility_AtkWrapper_*` (`jni/src/AtkWrapper.c`).

### Native JAW thread (GLib main loop thread)

#### Thread startup

During `loadAtkBridge()` (`jni/src/AtkWrapper.c`), native code starts a dedicated thread (`JavaAtkWrapper-MainLoop`) that runs the GLib main loop.
On ATSPI 2.33.1+, this thread uses a dedicated `GMainContext`; on older versions, it uses the default context.

#### Event flow

JNI callbacks queue handlers through `jni_main_idle_add(...)` (`jni/src/AtkWrapper.c`).
Those handlers run on the GLib loop thread and emit ATK/AT-SPI signals there.

The GLib loop thread (`jaw_loop_callback`, `jni/src/AtkWrapper.c`) processes events in a `while (!jaw_loop_exit_requested)` cycle.
On each iteration, it creates a JNI local frame (`PushLocalFrame`), runs one blocking GLib iteration (`g_main_context_iteration(..., TRUE)`),
clears pending JNI exceptions (`jaw_jni_clear_exception`, `jni/src/AtkWrapper.c`), and then releases local references (`PopLocalFrame`).
Because this thread-level loop already manages the JNI local frame, handler functions that run on this thread do not push/pop local frames themselves.

#### Access to Swing data

For AT-SPI requests that need Swing data, Java adapter methods call
`AtkUtil.invokeInSwingAndWait(...)` / `AtkUtil.invokeInSwing(...)` (`wrapper/org/GNOME/Accessibility/AtkUtil.java`) to run Swing access on the EDT.

## Object Lifecycle

Each accessible UI element is represented across three layers:
- Java accessibility objects (`javax.accessibility.Accessible` / `AccessibleContext`).
- Native ATK objects (`JawImpl`, `JawObject`, `JawHyperlink`, `JawToplevel`).
- ATK Interfaces and Java Adapter Classes (`AtkText`, `AtkAction`, etc.).

### Java `Accessible` / `AccessibleContext`
**Description**
Java-side accessibility objects exposed by Swing/AWT components.
They provide the source data for native ATK requests.

**Native Peer Registration**
- Java calls `AtkWrapperDisposer.getInstance().addRecord(ac)` when a native peer is needed.
- If `ac` is not already registered:
  - `AtkWrapperDisposer` calls `AtkWrapper.createNativeResources(ac)`.
  - It stores the `AccessibleContext` -> native pointer mapping.

**Cleanup**
When a context becomes unreachable, `AtkWrapperDisposer` receives the phantom-reference event and triggers native release via `releaseNativeResources(...)`.

### Native ATK objects

* #### JawImpl (`jni/src/jawimpl.h`)

  * **Description** 
    
    JawImpl is the object that represents a Java accessibility node (AccessibleContext) as an ATK object.

  * **Creation**
    
      `AtkWrapper.createNativeResources(...)` -> JNI `Java_org_GNOME_Accessibility_AtkWrapper_createNativeResources(...)` -> `jaw_impl_create_instance(...)`.
        `jaw_impl_create_instance(...)` allocates the object, stores a weak JNI global reference to `AccessibleContext`, and aggregates supported ATK interfaces.

  * **Cleanup**

      `AtkWrapper.releaseNativeResources(...)` schedules `g_object_unref(...)`. Final cleanup runs in `jaw_impl_finalize(...)`.

* #### JawObject (`jni/src/jawobject.h`)
   
  * **Description**

      Base `AtkObject` wrapper used by `JawImpl`.

  * **Creation**

      Constructed as the parent part of `JawImpl` during `g_object_new(JAW_TYPE_IMPL(...))`. Initializes shared state in `jaw_object_init(...)`.

  * **Cleanup** `jaw_object_finalize(...)`

* #### JawHyperlink (`jni/src/jawhyperlink.h`)

    * **Description**

      Native `AtkHyperlink` object used for hyperlink-specific API calls.

  * **Creation**

    Created on demand via `jaw_hyperlink_new(...)` (for hypertext link access). Stores a JNI global reference to the Java hyperlink adapter.

  * **Cleanup** `jaw_hyperlink_finalize(...)`

* #### JawToplevel (`jni/src/jawtoplevel.h`)

    * **Description**

      Application root ATK object that contains top-level windows.

  * **Creation**

    Lazily created in `jaw_util_get_root()` with `g_object_new(JAW_TYPE_TOPLEVEL, NULL)` and initialized via `atk_object_initialize(...)`.

  * **Cleanup** `jaw_toplevel_object_finalize(...)`

### ATK Interfaces and Java Adapter Classes

**Description**
Native `jaw_*` interface handlers delegate ATK requests to Java adapter classes in `wrapper/org/GNOME/Accessibility/Atk*.java`. Adapters read Swing `AccessibleContext` data and return results to native code.

**Creation**
During `JawImpl` setup, interface-specific data is created in `jaw_*_data_init(...)` (for example `jaw_action_data_init(...)`, `jaw_text_data_init(...)`).

**Cleanup**
In `jaw_impl_finalize(...)`, each interface entry invokes its `jaw_*_data_finalize(...)`

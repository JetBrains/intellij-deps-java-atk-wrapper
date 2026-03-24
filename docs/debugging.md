# Debugging

This section describes the available debugging mechanisms for the Java ATK Wrapper and related accessibility tooling.

## JAW_DEBUG (Stack Traces)

#### TODO: Consider removing this macro in favor of GLib logging.

The `JAW_DEBUG` macro is primarily used for logging code stacktraces. 

### Output file

When enabled, debug messages are written to `jaw_log_file.txt`.

### Enabling JAW_DEBUG

#### Option 1

Set the JAW_DEBUG environment variable to a non-zero value:

```bash
export JAW_DEBUG=1
```

#### Option 2

Add the variable to your shell profile (e.g., ~/.bashrc).

## GLib Logging Functions

For general debugging purposes, use the GLib logging functions.

### Common Logging Functions:
- `g_debug()` - Debug messages (disabled by default)
- `g_message()` - Informational messages
- `g_warning()` - Warning messages
- `g_critical()` - Critical warnings
- `g_error()` - Fatal errors (terminates the program)

### Example

```c
g_warning("%s: jniEnv is NULL", G_STRFUNC);
```

### More information:
https://docs.gtk.org/glib/logging.html#message-logging

## Debugging Orca Screen Reader

Orca debug output can help trace accessibility events and AT-SPI interactions.

### Enable Debug Mode

   ```bash
   orca --debug
   ```

### Write Debug Output to a file

   ```bash
   orca --debug-file=/path/to/debug.out
   ```

#### More information:
https://orca.gnome.org/debugging

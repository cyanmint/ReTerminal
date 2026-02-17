# ReTerminal
**ReTerminal** is a sleek, Material 3-inspired terminal emulator designed as a modern alternative to the legacy [Jackpal Terminal](https://github.com/jackpal/Android-Terminal-Emulator). Built on [Termux's](https://github.com/termux/termux-app) robust TerminalView

Download the latest APK from the [Releases Section](https://github.com/RohitKushvaha01/ReTerminal/releases/latest).

# Features
- [x] Basic Terminal
- [x] Virtual Keys
- [x] Multiple Sessions
- [x] Alpine Linux support
  - [x] PRoot mode (rootless)
  - [x] Chroot mode (requires root)

# Screenshots
<div>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="32%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/02.jpg" width="32%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/03.jpg" width="32%" />
</div>

## Community
> [!TIP]
Join the reTerminal community to stay updated and engage with other users:
- [Telegram](https://t.me/reTerminal)


# FAQ

### **Q: Why do I get a "Permission Denied" error when trying to execute a binary or script?**
**A:** This happens because ReTerminal runs on the latest Android API, which enforces **W^X restrictions**. Since files in `$PREFIX` or regular storage directories can't be executed directly, you need to use one of the following workarounds:

---

### **Option 1: Use the Dynamic Linker (for Binaries)**
If you're trying to run a binary (not a script), you can use the dynamic linker to execute it:

```bash
$LINKER /absolute/path/to/binary
```

✅ **Note:** This method won't work for **statically linked binaries** (binaries without external dependencies).

---

### **Option 2: Use `sh` for Scripts**
If you're trying to execute a shell script, simply use `sh` to run it:

```bash
sh /path/to/script
```

This bypasses the need for execute permissions since the script is interpreted by the shell.

---

### **Option 3: Use Shizuku for Full Shell Access (Recommended)**
If you have **Shizuku** installed, you can gain shell access to `/data/local/tmp`, which has executable permissions. This is the easiest way to run binaries without restrictions.

---

## Alpine Container Modes

ReTerminal supports two modes for running the Alpine Linux container:

### **PRoot Mode (Default)**
- **Requirements:** No root access needed
- **How it works:** Uses PRoot to create a sandboxed environment without requiring system-level privileges
- **Best for:** Regular users without root access

To switch container modes, go to **Settings** → "Alpine Container mode" and select your preferred mode.

### **Chroot Mode**
- **Requirements:** Root access (via su or Magisk)
- **How it works:** Uses `chroot` with optional namespace isolation for the Alpine container
- **Benefits:** 
  - Better performance compared to PRoot
  - More native container experience
  - Configurable isolation levels
- **How to enable:** Go to Settings → Alpine Container mode → Select "Chroot"

#### **Chroot Options**

When using Chroot mode, you can configure three additional options that can work independently or in combination:

1. **Use unshare** (default: ON)
   - When enabled: Creates isolated namespaces (mount, PID, UTS, IPC) for better isolation
   - When disabled: Uses basic chroot without namespace isolation (lighter but less isolated)
   
2. **Share namespace** (default: OFF, requires unshare to be ON)
   - When enabled: All terminal sessions share the same namespace (processes visible across sessions)
   - When disabled: Each session gets its own isolated namespace (processes not visible across sessions)
   - **Can be combined with PID 1** for shared namespace with init daemon

3. **Ensure PID 1** (default: OFF, requires unshare to be ON)
   - When enabled: First session's init process becomes PID 1 in the Alpine container
   - Important for proper signal handling and orphan process reaping
   - Required by some init systems (like systemd)
   - **Can be combined with shared namespace** for namespace daemon behavior

**Configuration examples:**
- **Maximum isolation (default):** Unshare ON, Share namespace OFF, PID 1 OFF
  - Each session fully isolated in its own namespace
- **Shared environment:** Unshare ON, Share namespace ON, PID 1 OFF
  - All sessions share processes/mounts, can interact with each other
- **Proper init system (isolated):** Unshare ON, Share namespace OFF, PID 1 ON
  - Each session has init as PID 1, proper signal handling
- **Shared namespace with init daemon:** Unshare ON, Share namespace ON, PID 1 ON ⭐
  - First session creates shared namespace with init as PID 1 (daemon)
  - Subsequent sessions join the namespace and see init as PID 1
  - Best for persistent services and proper process management
- **Minimal overhead:** Unshare OFF
  - Basic chroot, no namespace isolation

To configure these options, go to **Settings** → Select "Chroot" container mode → Configure "Chroot Options".


## Found this app useful? :heart:
Support it by giving a star :star: <br>
Also, **__[follow](https://github.com/Rohitkushvaha01)__** me for my next creations!


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

### **Chroot Mode**
- **Requirements:** Root access (via su or Magisk)
- **How it works:** Uses `chroot` and `unshare` to create isolated namespaces for the Alpine container
- **Benefits:** 
  - Better performance compared to PRoot
  - More native container experience
  - Proper namespace isolation
- **How to enable:** Go to Settings → Alpine Container mode → Select "Chroot"
- **Note:** New sessions will enter the corresponding namespace

To switch between modes, go to **Settings** and select your preferred container mode under "Alpine Container mode".


## Found this app useful? :heart:
Support it by giving a star :star: <br>
Also, **__[follow](https://github.com/Rohitkushvaha01)__** me for my next creations!


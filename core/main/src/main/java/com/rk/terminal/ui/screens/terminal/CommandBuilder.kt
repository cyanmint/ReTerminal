package com.rk.terminal.ui.screens.terminal

import com.rk.libcommons.alpineDir
import com.rk.libcommons.localBinDir
import com.rk.libcommons.localDir
import com.rk.terminal.service.NamespaceSessionDaemon
import com.rk.terminal.ui.screens.settings.ContainerMode
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Builds command arrays for different container execution modes.
 * Commands are executed separately instead of bundled into shell strings.
 */
object CommandBuilder {
    
    /**
     * Create wrapper script for nsenter into existing namespace
     */
    private fun createNsenterScript(alpineDir: File, nsPid: String): File {
        val scriptFile = File(localBinDir(), "nsenter-shared.sh")
        val scriptContent = """#!/system/bin/sh
# Script to enter existing shared namespace

ALPINE_DIR="${alpineDir.absolutePath}"
NS_PID="$nsPid"

# Enter namespace and start shell in background
echo "[DEBUG] Executing: nsenter -t ${'$'}NS_PID -m -p -u -i chroot ${'$'}ALPINE_DIR /bin/sh -c 'cd /root && (setsid /bin/sh </dev/null >/dev/null 2>&1 &) && sleep 0.1'" >&2
nsenter -t "${'$'}NS_PID" -m -p -u -i chroot "${'$'}ALPINE_DIR" /bin/sh -c 'cd /root && (setsid /bin/sh </dev/null >/dev/null 2>&1 &) && sleep 0.1'
"""
        scriptFile.writeText(scriptContent)
        // Set owner-only read/write permissions (no execute needed since we use sh script.sh)
        scriptFile.setReadable(true, true)
        scriptFile.setWritable(true, true)
        return scriptFile
    }
    
    /**
     * Create wrapper script for basic chroot (no namespace)
     */
    private fun createBasicChrootScript(alpineDir: File): File {
        val scriptFile = File(localBinDir(), "chroot-basic.sh")
        val scriptContent = """#!/system/bin/sh
# Script for basic chroot without namespace

ALPINE_DIR="${alpineDir.absolutePath}"

# Execute chroot and start shell in /root
echo "[DEBUG] Executing: chroot ${'$'}ALPINE_DIR /bin/sh -c 'cd /root && exec /bin/sh'" >&2
chroot "${'$'}ALPINE_DIR" /bin/sh -c 'cd /root && exec /bin/sh'
"""
        scriptFile.writeText(scriptContent)
        // Set owner-only read/write permissions (no execute needed since we use sh script.sh)
        scriptFile.setReadable(true, true)
        scriptFile.setWritable(true, true)
        return scriptFile
    }
    
    /**
     * Create wrapper script for isolated chroot setup
     */
    private fun createIsolatedChrootScript(alpineDir: File): File {
        val scriptFile = File(localBinDir(), "chroot-isolated.sh")
        val scriptContent = """#!/system/bin/sh
# Script to setup and enter isolated chroot namespace

ALPINE_DIR="${alpineDir.absolutePath}"

# Mount proc filesystem
echo "[DEBUG] Executing: mount -t proc proc ${'$'}ALPINE_DIR/proc" >&2
mount -t proc proc "${'$'}ALPINE_DIR/proc" 2>/dev/null || true

# Bind mount Android directories
echo "[DEBUG] Executing: mount --bind /sdcard ${'$'}ALPINE_DIR/sdcard" >&2
mount --bind /sdcard "${'$'}ALPINE_DIR/sdcard" 2>/dev/null || true
echo "[DEBUG] Executing: mount --bind /storage ${'$'}ALPINE_DIR/storage" >&2
mount --bind /storage "${'$'}ALPINE_DIR/storage" 2>/dev/null || true
echo "[DEBUG] Executing: mount --bind /data/data ${'$'}ALPINE_DIR/data/data" >&2
mount --bind /data/data "${'$'}ALPINE_DIR/data/data" 2>/dev/null || true
echo "[DEBUG] Executing: mount --bind /system ${'$'}ALPINE_DIR/system" >&2
mount --bind /system "${'$'}ALPINE_DIR/system" 2>/dev/null || true
echo "[DEBUG] Executing: mount --bind /vendor ${'$'}ALPINE_DIR/vendor" >&2
mount --bind /vendor "${'$'}ALPINE_DIR/vendor" 2>/dev/null || true

# Execute chroot with init as PID 1
echo "[DEBUG] Executing: exec chroot ${'$'}ALPINE_DIR /sbin/init" >&2
exec chroot "${'$'}ALPINE_DIR" /sbin/init
"""
        scriptFile.writeText(scriptContent)
        // Set owner-only read/write permissions (no execute needed since we use sh script.sh)
        scriptFile.setReadable(true, true)
        scriptFile.setWritable(true, true)
        return scriptFile
    }
    
    /**
     * Create wrapper script for shared chroot setup
     */
    private fun createSharedChrootScript(alpineDir: File, pidFile: File): File {
        val scriptFile = File(localBinDir(), "chroot-shared.sh")
        val scriptContent = """#!/system/bin/sh
# Script to setup and enter shared chroot namespace

ALPINE_DIR="${alpineDir.absolutePath}"
PID_FILE="${pidFile.absolutePath}"

# Save the PID for future sessions to join this namespace
echo "[DEBUG] Saving PID ${'$'}${'$'} to ${'$'}PID_FILE" >&2
echo ${'$'}${'$'} > "${'$'}PID_FILE"

# Mount proc filesystem
echo "[DEBUG] Executing: mount -t proc proc ${'$'}ALPINE_DIR/proc" >&2
mount -t proc proc "${'$'}ALPINE_DIR/proc" 2>/dev/null || true

# Bind mount Android directories
echo "[DEBUG] Executing: mount --bind /sdcard ${'$'}ALPINE_DIR/sdcard" >&2
mount --bind /sdcard "${'$'}ALPINE_DIR/sdcard" 2>/dev/null || true
echo "[DEBUG] Executing: mount --bind /storage ${'$'}ALPINE_DIR/storage" >&2
mount --bind /storage "${'$'}ALPINE_DIR/storage" 2>/dev/null || true
echo "[DEBUG] Executing: mount --bind /data/data ${'$'}ALPINE_DIR/data/data" >&2
mount --bind /data/data "${'$'}ALPINE_DIR/data/data" 2>/dev/null || true
echo "[DEBUG] Executing: mount --bind /system ${'$'}ALPINE_DIR/system" >&2
mount --bind /system "${'$'}ALPINE_DIR/system" 2>/dev/null || true
echo "[DEBUG] Executing: mount --bind /vendor ${'$'}ALPINE_DIR/vendor" >&2
mount --bind /vendor "${'$'}ALPINE_DIR/vendor" 2>/dev/null || true

# Launch init in background and wait
echo "[DEBUG] Executing: chroot ${'$'}ALPINE_DIR /sbin/init (in background)" >&2
(chroot "${'$'}ALPINE_DIR" /sbin/init &)
wait
"""
        scriptFile.writeText(scriptContent)
        // Set owner-only read/write permissions (no execute needed since we use sh script.sh)
        scriptFile.setReadable(true, true)
        scriptFile.setWritable(true, true)
        return scriptFile
    }
    
    /**
     * Build command array for PRoot mode
     */
    fun buildProotCommand(
        alpineDir: File,
        nativeLibDir: String,
        prefix: String
    ): Array<String> {
        val prootPath = File(nativeLibDir, "libproot.so").absolutePath
        val prootBinPath = File(localBinDir(), "proot").absolutePath
        
        // Copy libproot.so to local bin directory if it doesn't exist
        val prootSrc = File(prootPath)
        val prootDest = File(prootBinPath)
        if (!prootDest.exists() || prootDest.length() != prootSrc.length()) {
            prootSrc.copyTo(prootDest, overwrite = true)
            prootDest.setExecutable(true)
            prootDest.setReadable(true)
            prootDest.setWritable(true)
        }
        
        // Determine which linker to use (64-bit or 32-bit)
        val linker = if (File("/system/bin/linker64").exists()) {
            "/system/bin/linker64"
        } else {
            "/system/bin/linker"
        }
        
        return arrayOf(
            linker,
            prootBinPath,
            "-r", alpineDir.absolutePath,
            "-b", "/sdcard:/sdcard",
            "-b", "/storage:/storage",
            "-b", "/data/data:/data/data",
            "-b", "/system:/system",
            "-b", "/vendor:/vendor",
            "-b", "/apex:/apex",
            "-b", "/linkerconfig:/linkerconfig",
            "-b", "${localDir().absolutePath}/stat:/proc/stat",
            "-b", "${localDir().absolutePath}/vmstat:/proc/vmstat",
            "-b", "/proc/self/fd:/proc/self/fd",
            "-b", "/proc/self/fd/0:/dev/stdin",
            "-b", "/proc/self/fd/1:/dev/stdout",
            "-b", "/proc/self/fd/2:/dev/stderr",
            "-b", "/dev/urandom:/dev/random",
            "-w", "/root",
            "/bin/sh",
            "-c",
            "cd /root && exec /bin/sh"
        )
    }
    
    /**
     * Build command array for basic chroot (no namespace)
     * Mounts are executed separately before this command
     */
    fun buildChrootCommand(
        alpineDir: File,
        useSu: Boolean
    ): Array<String> {
        val chrootPath = alpineDir.absolutePath
        
        return if (useSu) {
            // For root mode, we need to use a wrapper script
            val suWrapper = File(localBinDir(), "su-wrapper-basic.sh")
            suWrapper.writeText("""#!/system/bin/sh
echo "[DEBUG] Executing: su -c \"chroot $chrootPath /bin/sh -c 'cd /root && exec /bin/sh'\"" >&2
exec su -c "chroot $chrootPath /bin/sh -c 'cd /root && exec /bin/sh'"
""")
            suWrapper.setReadable(true, true)
            suWrapper.setWritable(true, true)
            arrayOf("sh", suWrapper.absolutePath)
        } else {
            // Without su, execute chroot directly
            arrayOf("chroot", chrootPath, "/bin/sh", "-c", "cd /root && exec /bin/sh")
        }
    }
    
    /**
     * Build command array for chroot with isolated namespace
     * When unsharing, use /sbin/init to make it PID 1 (default behavior)
     * Mounts are executed inside the namespace via a wrapper script
     */
    fun buildChrootIsolatedCommand(
        alpineDir: File,
        useSu: Boolean
    ): Array<String> {
        // Create wrapper script that will execute inside the unshare namespace
        val scriptFile = createIsolatedChrootScript(alpineDir)
        val scriptPath = scriptFile.absolutePath
        
        return if (useSu) {
            // Create a wrapper that calls su with unshare
            val suWrapper = File(localBinDir(), "su-wrapper-isolated.sh")
            suWrapper.writeText("""#!/system/bin/sh
echo "[DEBUG] Executing: su -c \"unshare -a -f /system/bin/sh $scriptPath\"" >&2
su -c "unshare -a -f /system/bin/sh $scriptPath"
""")
            suWrapper.setReadable(true, true)
            suWrapper.setWritable(true, true)
            arrayOf("sh", suWrapper.absolutePath)
        } else {
            arrayOf("unshare", "-a", "-f", "/system/bin/sh", scriptPath)
        }
    }
    
    /**
     * Build command array for chroot with shared namespace
     * First session: unshare and use /sbin/init as PID 1
     * Subsequent sessions: nsenter and use /bin/sh
     */
    fun buildChrootSharedCommand(
        alpineDir: File,
        prefix: String,
        useSu: Boolean
    ): Array<String> {
        val chrootPath = alpineDir.absolutePath
        val namespacePidFile = File(localDir().absolutePath, ".alpine-ns-pid")
        
        // Check if namespace already exists
        val nsInfo = runBlocking {
            NamespaceSessionDaemon.registerSession(prefix)
        }
        
        // Determine if this is the first session
        // Try to read PID file to verify namespace actually exists
        val isFirstSession = if (nsInfo?.sessionCount == 1) {
            true  // Daemon says first session
        } else {
            // Daemon says session exists, but verify PID file is readable
            try {
                val pidExists = namespacePidFile.exists() && namespacePidFile.canRead()
                !pidExists  // If file doesn't exist or can't be read, treat as first session
            } catch (e: Exception) {
                true  // On any error checking file, treat as first session
            }
        }
        
        return if (isFirstSession) {
            // First session: create namespace with /sbin/init as PID 1
            // Create wrapper script that will execute inside the unshare namespace
            val scriptFile = createSharedChrootScript(alpineDir, namespacePidFile)
            val scriptPath = scriptFile.absolutePath
            
            if (useSu) {
                // Create a wrapper that calls su with unshare
                val suWrapper = File(localBinDir(), "su-wrapper-shared.sh")
                suWrapper.writeText("""#!/system/bin/sh
echo "[DEBUG] Executing: su -c \"unshare -a -f /system/bin/sh $scriptPath\"" >&2
su -c "unshare -a -f /system/bin/sh $scriptPath"
""")
                suWrapper.setReadable(true, true)
                suWrapper.setWritable(true, true)
                arrayOf("sh", suWrapper.absolutePath)
            } else {
                arrayOf("unshare", "-a", "-f", "/system/bin/sh", scriptPath)
            }
        } else {
            // Subsequent sessions: join existing namespace
            try {
                val nsPid = namespacePidFile.readText().trim()
                
                // Create wrapper script for nsenter
                val scriptFile = createNsenterScript(alpineDir, nsPid)
                val scriptPath = scriptFile.absolutePath
                
                if (useSu) {
                    // Create a wrapper that calls su with sh
                    val suWrapper = File(localBinDir(), "su-wrapper-nsenter.sh")
                    suWrapper.writeText("""#!/system/bin/sh
echo "[DEBUG] Executing: su -c \"/system/bin/sh $scriptPath\"" >&2
su -c "/system/bin/sh $scriptPath"
""")
                    suWrapper.setReadable(true, true)
                    suWrapper.setWritable(true, true)
                    arrayOf("sh", suWrapper.absolutePath)
                } else {
                    arrayOf("/system/bin/sh", scriptPath)
                }
            } catch (e: Exception) {
                // If we can't read PID file, fall back to creating new namespace
                val scriptFile = createSharedChrootScript(alpineDir, namespacePidFile)
                val scriptPath = scriptFile.absolutePath
                
                if (useSu) {
                    // Create a wrapper that calls su with unshare
                    val suWrapper = File(localBinDir(), "su-wrapper-shared-fallback.sh")
                    suWrapper.writeText("""#!/system/bin/sh
echo "[DEBUG] Executing: su -c \"unshare -a -f /system/bin/sh $scriptPath\"" >&2
su -c "unshare -a -f /system/bin/sh $scriptPath"
""")
                    suWrapper.setReadable(true, true)
                    suWrapper.setWritable(true, true)
                    arrayOf("sh", suWrapper.absolutePath)
                } else {
                    arrayOf("unshare", "-a", "-f", "/system/bin/sh", scriptPath)
                }
            }
        }
    }
    
    /**
     * Main entry point to build command based on settings
     * Also handles prerequisite setup like mounts for basic chroot
     */
    fun buildCommand(
        containerMode: Int,
        useUnshare: Boolean,
        shareNamespace: Boolean,
        useSu: Boolean,
        alpineDir: File,
        nativeLibDir: String,
        prefix: String
    ): Array<String> {
        // For basic chroot (no namespace), setup mounts before returning command
        if (containerMode == ContainerMode.CHROOT && !useUnshare) {
            ContainerSetup.setupMounts(alpineDir, useSu)
        }
        
        return when {
            containerMode == ContainerMode.PROOT -> {
                buildProotCommand(alpineDir, nativeLibDir, prefix)
            }
            !useUnshare -> {
                buildChrootCommand(alpineDir, useSu)
            }
            shareNamespace -> {
                buildChrootSharedCommand(alpineDir, prefix, useSu)
            }
            else -> {
                buildChrootIsolatedCommand(alpineDir, useSu)
            }
        }
    }
}

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
mount -t proc proc "${'$'}ALPINE_DIR/proc" 2>/dev/null || true

# Bind mount Android directories
mount --bind /sdcard "${'$'}ALPINE_DIR/sdcard" 2>/dev/null || true
mount --bind /storage "${'$'}ALPINE_DIR/storage" 2>/dev/null || true
mount --bind /data/data "${'$'}ALPINE_DIR/data/data" 2>/dev/null || true
mount --bind /system "${'$'}ALPINE_DIR/system" 2>/dev/null || true
mount --bind /vendor "${'$'}ALPINE_DIR/vendor" 2>/dev/null || true

# Execute chroot with init as PID 1
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
echo ${'$'}${'$'} > "${'$'}PID_FILE"

# Mount proc filesystem
mount -t proc proc "${'$'}ALPINE_DIR/proc" 2>/dev/null || true

# Bind mount Android directories
mount --bind /sdcard "${'$'}ALPINE_DIR/sdcard" 2>/dev/null || true
mount --bind /storage "${'$'}ALPINE_DIR/storage" 2>/dev/null || true
mount --bind /data/data "${'$'}ALPINE_DIR/data/data" 2>/dev/null || true
mount --bind /system "${'$'}ALPINE_DIR/system" 2>/dev/null || true
mount --bind /vendor "${'$'}ALPINE_DIR/vendor" 2>/dev/null || true

# Launch init in background and wait
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
        
        return arrayOf(
            prootPath,
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
        // Create wrapper script for chroot execution
        val scriptFile = createBasicChrootScript(alpineDir)
        val scriptPath = scriptFile.absolutePath
        
        return if (useSu) {
            arrayOf("su", "-c", "/system/bin/sh $scriptPath")
        } else {
            arrayOf("/system/bin/sh", scriptPath)
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
            arrayOf("su", "-c", "unshare -a -f /system/bin/sh $scriptPath")
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
        
        // Treat as first session if: 
        // 1. NamespaceSessionDaemon says it's the first session, OR
        // 2. PID file doesn't exist (edge case: daemon says session exists but file is missing)
        val isFirstSession = nsInfo?.sessionCount == 1 || !namespacePidFile.exists()
        
        return if (isFirstSession) {
            // First session: create namespace with /sbin/init as PID 1
            // Create wrapper script that will execute inside the unshare namespace
            val scriptFile = createSharedChrootScript(alpineDir, namespacePidFile)
            val scriptPath = scriptFile.absolutePath
            
            if (useSu) {
                arrayOf("su", "-c", "unshare -a -f /system/bin/sh $scriptPath")
            } else {
                arrayOf("unshare", "-a", "-f", "/system/bin/sh", scriptPath)
            }
        } else {
            // Subsequent sessions: join existing namespace
            val nsPid = namespacePidFile.readText().trim()
            
            // Create wrapper script for nsenter
            val scriptFile = createNsenterScript(alpineDir, nsPid)
            val scriptPath = scriptFile.absolutePath
            
            if (useSu) {
                arrayOf("su", "-c", "/system/bin/sh $scriptPath")
            } else {
                arrayOf("/system/bin/sh", scriptPath)
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

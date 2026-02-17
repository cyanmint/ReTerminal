package com.rk.terminal.ui.screens.terminal

import com.rk.libcommons.alpineDir
import com.rk.libcommons.localDir
import com.rk.terminal.service.NamespaceSessionDaemon
import com.rk.terminal.ui.screens.settings.ContainerMode
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Builds command arrays for different container execution modes.
 * Eliminates the need for intermediate shell scripts.
 */
object CommandBuilder {
    
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
     */
    fun buildChrootCommand(
        alpineDir: File,
        useSu: Boolean
    ): Array<String> {
        val mountCommands = buildMountCommands(alpineDir)
        val chrootCmd = "chroot ${alpineDir.absolutePath} /bin/sh -c 'cd /root && exec /bin/sh'"
        
        val shellCommand = if (mountCommands.isNotEmpty()) {
            "$mountCommands && $chrootCmd"
        } else {
            chrootCmd
        }
        
        return if (useSu) {
            arrayOf("su", "-c", "sh -c \"$shellCommand\"")
        } else {
            arrayOf("sh", "-c", shellCommand)
        }
    }
    
    /**
     * Build command array for chroot with isolated namespace
     * When unsharing, use /sbin/init to make it PID 1 (default behavior)
     */
    fun buildChrootIsolatedCommand(
        alpineDir: File,
        useSu: Boolean
    ): Array<String> {
        val mountCommands = buildMountCommands(alpineDir)
        val procMount = "mount -t proc proc ${alpineDir.absolutePath}/proc 2>/dev/null || true"
        // Use /sbin/init when unsharing to make it PID 1
        val chrootCmd = "chroot ${alpineDir.absolutePath} /sbin/init"
        
        val shellCommand = "unshare -a -f sh -c '$procMount && $mountCommands && exec $chrootCmd'"
        
        return if (useSu) {
            arrayOf("su", "-c", "sh -c \"$shellCommand\"")
        } else {
            arrayOf("sh", "-c", shellCommand)
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
        val namespacePidFile = File(localDir().absolutePath, ".alpine-ns-pid")
        
        // Check if namespace already exists
        val nsInfo = runBlocking {
            NamespaceSessionDaemon.registerSession(prefix)
        }
        
        val isFirstSession = nsInfo?.sessionCount == 1
        
        return if (isFirstSession) {
            // First session: create namespace with /sbin/init as PID 1
            val mountCommands = buildMountCommands(alpineDir)
            val procMount = "mount -t proc proc ${alpineDir.absolutePath}/proc 2>/dev/null || true"
            val savePid = "echo \$\$ > ${namespacePidFile.absolutePath}"
            // Use /sbin/init when unsharing to make it PID 1
            val chrootCmd = "chroot ${alpineDir.absolutePath} /sbin/init"
            
            // Run chroot in background and wait to keep namespace alive
            val unshareCmd = "unshare -a -f sh -c '$savePid && $procMount && $mountCommands && ($chrootCmd &) && wait'"
            
            if (useSu) {
                arrayOf("su", "-c", unshareCmd)
            } else {
                arrayOf("sh", "-c", unshareCmd)
            }
        } else {
            // Subsequent sessions: join existing namespace and use /bin/sh
            val nsPid = namespacePidFile.readText().trim()
            // Use /bin/sh when entering existing namespace (init already running as PID 1)
            val chrootCmd = "chroot ${alpineDir.absolutePath} /bin/sh -c 'cd /root && (setsid /bin/sh </dev/null >/dev/null 2>&1 &) && sleep 0.1'"
            val nsenterCmd = "nsenter -t $nsPid -m -p -u -i $chrootCmd"
            
            if (useSu) {
                arrayOf("su", "-c", nsenterCmd)
            } else {
                arrayOf("sh", "-c", nsenterCmd)
            }
        }
    }
    
    /**
     * Build mount commands for chroot
     */
    private fun buildMountCommands(alpineDir: File): String {
        val mounts = listOf(
            "mount --bind /sdcard ${alpineDir.absolutePath}/sdcard 2>/dev/null || true",
            "mount --bind /storage ${alpineDir.absolutePath}/storage 2>/dev/null || true",
            "mount --bind /data/data ${alpineDir.absolutePath}/data/data 2>/dev/null || true",
            "mount --bind /system ${alpineDir.absolutePath}/system 2>/dev/null || true",
            "mount --bind /vendor ${alpineDir.absolutePath}/vendor 2>/dev/null || true"
        )
        
        return mounts.joinToString(" && ")
    }
    
    /**
     * Main entry point to build command based on settings
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

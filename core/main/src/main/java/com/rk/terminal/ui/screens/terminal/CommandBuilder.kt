package com.rk.terminal.ui.screens.terminal

import com.rk.libcommons.alpineDir
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
        // Simple chroot command - mounts handled separately by ContainerSetup
        val chrootPath = alpineDir.absolutePath
        
        return if (useSu) {
            arrayOf("su", "-c", "chroot $chrootPath /bin/sh -c 'cd /root && exec /bin/sh'")
        } else {
            arrayOf("chroot", chrootPath, "/bin/sh", "-c", "cd /root && exec /bin/sh")
        }
    }
    
    /**
     * Build command array for chroot with isolated namespace
     * When unsharing, use /sbin/init to make it PID 1 (default behavior)
     * Mounts are executed inside the namespace
     */
    fun buildChrootIsolatedCommand(
        alpineDir: File,
        useSu: Boolean
    ): Array<String> {
        val chrootPath = alpineDir.absolutePath
        
        // Mount proc and bind mounts inside the namespace, then exec chroot with /sbin/init
        val setupAndChroot = """
            mount -t proc proc $chrootPath/proc 2>/dev/null || true &&
            mount --bind /sdcard $chrootPath/sdcard 2>/dev/null || true &&
            mount --bind /storage $chrootPath/storage 2>/dev/null || true &&
            mount --bind /data/data $chrootPath/data/data 2>/dev/null || true &&
            mount --bind /system $chrootPath/system 2>/dev/null || true &&
            mount --bind /vendor $chrootPath/vendor 2>/dev/null || true &&
            exec chroot $chrootPath /sbin/init
        """.trimIndent().replace("\n", " ")
        
        return if (useSu) {
            arrayOf("su", "-c", "unshare -a -f sh -c '$setupAndChroot'")
        } else {
            arrayOf("unshare", "-a", "-f", "sh", "-c", setupAndChroot)
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
        
        val isFirstSession = nsInfo?.sessionCount == 1
        
        return if (isFirstSession) {
            // First session: create namespace with /sbin/init as PID 1
            val pidFile = namespacePidFile.absolutePath
            val setupAndChroot = """
                echo $$ > $pidFile &&
                mount -t proc proc $chrootPath/proc 2>/dev/null || true &&
                mount --bind /sdcard $chrootPath/sdcard 2>/dev/null || true &&
                mount --bind /storage $chrootPath/storage 2>/dev/null || true &&
                mount --bind /data/data $chrootPath/data/data 2>/dev/null || true &&
                mount --bind /system $chrootPath/system 2>/dev/null || true &&
                mount --bind /vendor $chrootPath/vendor 2>/dev/null || true &&
                (chroot $chrootPath /sbin/init &) && wait
            """.trimIndent().replace("\n", " ")
            
            if (useSu) {
                arrayOf("su", "-c", "unshare -a -f sh -c '$setupAndChroot'")
            } else {
                arrayOf("unshare", "-a", "-f", "sh", "-c", setupAndChroot)
            }
        } else {
            // Subsequent sessions: join existing namespace and use /bin/sh
            val nsPid = namespacePidFile.readText().trim()
            val chrootShell = "chroot $chrootPath /bin/sh -c 'cd /root && (setsid /bin/sh </dev/null >/dev/null 2>&1 &) && sleep 0.1'"
            
            if (useSu) {
                arrayOf("su", "-c", "nsenter -t $nsPid -m -p -u -i $chrootShell")
            } else {
                arrayOf("nsenter", "-t", nsPid, "-m", "-p", "-u", "-i", "chroot", chrootPath, "/bin/sh", "-c", "cd /root && (setsid /bin/sh </dev/null >/dev/null 2>&1 &) && sleep 0.1")
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

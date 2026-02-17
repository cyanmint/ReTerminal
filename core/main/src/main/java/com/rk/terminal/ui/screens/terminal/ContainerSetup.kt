package com.rk.terminal.ui.screens.terminal

import android.util.Log
import java.io.File

/**
 * Handles container setup by executing prerequisite commands separately
 */
object ContainerSetup {
    private const val TAG = "ContainerSetup"
    
    /**
     * Execute a command and wait for completion
     */
    private fun executeCommand(command: Array<String>): Boolean {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val error = process.inputStream.bufferedReader().readText()
                Log.w(TAG, "Command failed (exit $exitCode): ${command.joinToString(" ")}\nError: $error")
            }
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command: ${command.joinToString(" ")}", e)
            false
        }
    }
    
    /**
     * Setup mounts for chroot container
     */
    fun setupMounts(alpineDir: File, useSu: Boolean): Boolean {
        val mounts = listOf(
            Triple("/sdcard", "${alpineDir.absolutePath}/sdcard", "sdcard access"),
            Triple("/storage", "${alpineDir.absolutePath}/storage", "storage access"),
            Triple("/data/data", "${alpineDir.absolutePath}/data/data", "app data access"),
            Triple("/system", "${alpineDir.absolutePath}/system", "system access"),
            Triple("/vendor", "${alpineDir.absolutePath}/vendor", "vendor access")
        )
        
        for ((source, target, description) in mounts) {
            // Create mount point if it doesn't exist
            val targetDir = File(target)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            
            // Execute mount command
            val mountCmd = if (useSu) {
                // Use array format to avoid quoting issues
                arrayOf("su", "-c", "mount --bind \"$source\" \"$target\"")
            } else {
                arrayOf("mount", "--bind", source, target)
            }
            
            // Try to mount, but don't fail if it doesn't work (some paths may not exist)
            executeCommand(mountCmd)
            Log.d(TAG, "Mounted $source -> $target ($description)")
        }
        
        return true
    }
    
    /**
     * Setup proc filesystem for namespace
     */
    fun setupProc(alpineDir: File, useSu: Boolean): Boolean {
        val procPath = "${alpineDir.absolutePath}/proc"
        val procCmd = if (useSu) {
            arrayOf("su", "-c", "mount -t proc proc \"$procPath\"")
        } else {
            arrayOf("mount", "-t", "proc", "proc", procPath)
        }
        
        return executeCommand(procCmd)
    }
    
    /**
     * Cleanup mounts when container stops
     */
    fun cleanupMounts(alpineDir: File, useSu: Boolean) {
        val mounts = listOf(
            "${alpineDir.absolutePath}/proc",
            "${alpineDir.absolutePath}/sdcard",
            "${alpineDir.absolutePath}/storage",
            "${alpineDir.absolutePath}/data/data",
            "${alpineDir.absolutePath}/system",
            "${alpineDir.absolutePath}/vendor"
        )
        
        for (mount in mounts) {
            val umountCmd = if (useSu) {
                arrayOf("su", "-c", "umount \"$mount\"")
            } else {
                arrayOf("umount", mount)
            }
            executeCommand(umountCmd)
        }
    }
}

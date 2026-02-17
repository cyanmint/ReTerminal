package com.rk.terminal.service

import android.util.Log
import com.rk.libcommons.child
import com.rk.settings.Settings
import com.rk.terminal.ui.screens.settings.ContainerMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Namespace Session Daemon
 * 
 * Manages shared namespace lifecycle for Alpine container sessions.
 * When shared namespace mode is enabled, this daemon:
 * - Tracks the primary namespace process
 * - Coordinates session creation and joining
 * - Manages namespace cleanup
 * - Monitors namespace health
 */
object NamespaceSessionDaemon {
    private const val TAG = "NamespaceSessionDaemon"
    
    // Namespace tracking data
    data class NamespaceInfo(
        val pid: Int,
        val pidFile: File,
        var sessionCount: Int = 0,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    // Active namespaces (key: namespace type, value: namespace info)
    private val namespaces = ConcurrentHashMap<String, NamespaceInfo>()
    
    // Mutex for thread-safe operations
    private val mutex = Mutex()
    
    // Namespace types
    private const val NS_TYPE_SHARED = "shared"
    private const val NS_TYPE_SHARED_PID1 = "shared-pid1"
    
    /**
     * Check if shared namespace mode is enabled
     */
    private fun isSharedNamespaceMode(): Boolean {
        return Settings.container_Mode == ContainerMode.CHROOT &&
               Settings.use_unshare &&
               Settings.share_namespace
    }
    
    /**
     * Check if PID 1 mode is enabled
     */
    private fun isPid1Mode(): Boolean {
        return Settings.ensure_pid_1
    }
    
    /**
     * Get the namespace type based on current settings
     */
    private fun getNamespaceType(): String {
        return if (isPid1Mode()) NS_TYPE_SHARED_PID1 else NS_TYPE_SHARED
    }
    
    /**
     * Get the PID file for the namespace type
     */
    fun getNamespacePidFile(prefix: String): File {
        val nsType = getNamespaceType()
        val fileName = when (nsType) {
            NS_TYPE_SHARED_PID1 -> ".alpine-ns-pid1"
            else -> ".alpine-ns-pid"
        }
        return File(prefix).child("local").child(fileName)
    }
    
    /**
     * Register a new session in the shared namespace
     */
    suspend fun registerSession(prefix: String): NamespaceInfo? = mutex.withLock {
        if (!isSharedNamespaceMode()) {
            Log.d(TAG, "Not in shared namespace mode, skipping registration")
            return null
        }
        
        val nsType = getNamespaceType()
        val pidFile = getNamespacePidFile(prefix)
        
        // Check if namespace already exists
        var nsInfo = namespaces[nsType]
        
        if (nsInfo == null) {
            // Try to read existing PID file
            if (pidFile.exists()) {
                try {
                    val pid = pidFile.readText().trim().toInt()
                    if (isProcessAlive(pid)) {
                        nsInfo = NamespaceInfo(pid, pidFile, 0)
                        namespaces[nsType] = nsInfo
                        Log.d(TAG, "Found existing namespace: type=$nsType, PID=$pid")
                    } else {
                        // Stale PID file, remove it
                        pidFile.delete()
                        Log.d(TAG, "Removed stale PID file: $pidFile")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading PID file: ${e.message}")
                    pidFile.delete()
                }
            }
        }
        
        // Increment session count
        nsInfo?.let {
            it.sessionCount++
            Log.d(TAG, "Session registered: type=$nsType, PID=${it.pid}, count=${it.sessionCount}")
        } ?: run {
            Log.d(TAG, "New namespace will be created: type=$nsType")
        }
        
        return nsInfo
    }
    
    /**
     * Notify daemon that namespace was created by shell script
     */
    suspend fun notifyNamespaceCreated(prefix: String, pid: Int) = mutex.withLock {
        if (!isSharedNamespaceMode()) return
        
        val nsType = getNamespaceType()
        val pidFile = getNamespacePidFile(prefix)
        
        val nsInfo = NamespaceInfo(pid, pidFile, 1)
        namespaces[nsType] = nsInfo
        
        Log.d(TAG, "Namespace created: type=$nsType, PID=$pid")
    }
    
    /**
     * Unregister a session from the shared namespace
     */
    suspend fun unregisterSession(prefix: String) = mutex.withLock {
        if (!isSharedNamespaceMode()) {
            return
        }
        
        val nsType = getNamespaceType()
        val nsInfo = namespaces[nsType] ?: return
        
        nsInfo.sessionCount--
        Log.d(TAG, "Session unregistered: type=$nsType, PID=${nsInfo.pid}, count=${nsInfo.sessionCount}")
        
        // If no more sessions, clean up namespace
        if (nsInfo.sessionCount <= 0) {
            cleanupNamespace(nsType, nsInfo)
        }
    }
    
    /**
     * Clean up a namespace
     */
    private fun cleanupNamespace(nsType: String, nsInfo: NamespaceInfo) {
        try {
            // Note: We don't kill the process here - it will exit naturally when the last session exits
            // The shell scripts handle process lifecycle
            
            // Just clean up our tracking
            namespaces.remove(nsType)
            Log.d(TAG, "Namespace cleaned up: type=$nsType, PID=${nsInfo.pid}")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up namespace: ${e.message}")
        }
    }
    
    /**
     * Check if a process is alive
     */
    private fun isProcessAlive(pid: Int): Boolean {
        return try {
            // Use su to check if process exists (in case it's a root process)
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -0 $pid"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get current namespace info
     */
    suspend fun getNamespaceInfo(prefix: String): NamespaceInfo? = mutex.withLock {
        if (!isSharedNamespaceMode()) return null
        
        val nsType = getNamespaceType()
        return namespaces[nsType]
    }
    
    /**
     * Clean up all stale namespaces
     */
    suspend fun cleanupStaleNamespaces() = mutex.withLock {
        val toRemove = mutableListOf<String>()
        
        namespaces.forEach { (type, info) ->
            if (!isProcessAlive(info.pid)) {
                toRemove.add(type)
                info.pidFile.delete()
                Log.d(TAG, "Removed stale namespace: type=$type, PID=${info.pid}")
            }
        }
        
        toRemove.forEach { namespaces.remove(it) }
    }
    
    /**
     * Get statistics about active namespaces
     */
    suspend fun getStats(): Map<String, Any> = mutex.withLock {
        return mapOf(
            "activeNamespaces" to namespaces.size,
            "namespaces" to namespaces.map { (type, info) ->
                mapOf(
                    "type" to type,
                    "pid" to info.pid,
                    "sessionCount" to info.sessionCount,
                    "uptime" to (System.currentTimeMillis() - info.createdAt)
                )
            }
        )
    }
    
    /**
     * Force cleanup of all namespaces (for app shutdown)
     */
    suspend fun shutdown() = mutex.withLock {
        namespaces.clear()
        Log.d(TAG, "Daemon shutdown - all namespaces cleared")
    }
}

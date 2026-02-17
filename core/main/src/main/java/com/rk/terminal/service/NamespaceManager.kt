package com.rk.terminal.service

import java.util.concurrent.ConcurrentHashMap

/**
 * Manages namespace sessions for chroot mode with unshare/nsenter
 */
object NamespaceManager {
    // Map of session ID to PID of init process in that namespace
    private val namespacePids = ConcurrentHashMap<String, Int>()
    
    // Track the first session's PID for FIRST_ONLY mode
    private var firstSessionPid: Int? = null
    
    /**
     * Register a new namespace session
     * @param sessionId The session identifier
     * @param pid The PID of the init process in the namespace
     */
    fun registerNamespace(sessionId: String, pid: Int) {
        namespacePids[sessionId] = pid
        if (firstSessionPid == null) {
            firstSessionPid = pid
        }
    }
    
    /**
     * Unregister a namespace session
     * @param sessionId The session identifier
     */
    fun unregisterNamespace(sessionId: String) {
        val pid = namespacePids.remove(sessionId)
        // If we removed the first session, clear it
        if (pid == firstSessionPid && namespacePids.isEmpty()) {
            firstSessionPid = null
        }
    }
    
    /**
     * Get the PID to nsenter for FIRST_ONLY mode
     * @return The PID of the first session, or null if no sessions exist
     */
    fun getFirstSessionPid(): Int? {
        return firstSessionPid
    }
    
    /**
     * Check if any namespace sessions exist
     */
    fun hasActiveSessions(): Boolean {
        return namespacePids.isNotEmpty()
    }
    
    /**
     * Get the PID for a specific session
     */
    fun getSessionPid(sessionId: String): Int? {
        return namespacePids[sessionId]
    }
    
    /**
     * Clear all namespace tracking (should only be called when all sessions are terminated)
     */
    fun clear() {
        namespacePids.clear()
        firstSessionPid = null
    }
}

package com.rk.terminal.service

import java.util.concurrent.ConcurrentHashMap

/**
 * Manages namespace sessions for chroot mode with unshare/nsenter
 */
object NamespaceManager {
    // Set of active session IDs in chroot mode
    private val activeSessions = ConcurrentHashMap.newKeySet<String>()
    
    // Track the first session ID for FIRST_ONLY mode
    private var firstSessionId: String? = null
    
    /**
     * Register a new namespace session
     * @param sessionId The session identifier
     */
    fun registerNamespace(sessionId: String) {
        activeSessions.add(sessionId)
        if (firstSessionId == null) {
            firstSessionId = sessionId
        }
    }
    
    /**
     * Unregister a namespace session
     * @param sessionId The session identifier
     */
    fun unregisterNamespace(sessionId: String) {
        activeSessions.remove(sessionId)
        // If we removed the first session, clear it
        if (sessionId == firstSessionId && activeSessions.isEmpty()) {
            firstSessionId = null
        }
    }
    
    /**
     * Check if the first session exists
     * @return true if first session is active
     */
    fun hasFirstSession(): Boolean {
        return firstSessionId != null && activeSessions.contains(firstSessionId)
    }
    
    /**
     * Check if any namespace sessions exist
     */
    fun hasActiveSessions(): Boolean {
        return activeSessions.isNotEmpty()
    }
    
    /**
     * Clear all namespace tracking (should only be called when all sessions are terminated)
     */
    fun clear() {
        activeSessions.clear()
        firstSessionId = null
    }
}

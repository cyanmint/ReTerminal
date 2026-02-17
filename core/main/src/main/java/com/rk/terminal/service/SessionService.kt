package com.rk.terminal.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.settings.Settings
import com.rk.terminal.ui.screens.terminal.MkSession
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SessionService : Service() {
    private val sessions = hashMapOf<String, TerminalSession>()
    val sessionList = mutableStateMapOf<String,Int>()
    var currentSession = mutableStateOf(Pair("main",com.rk.settings.Settings.working_Mode))

    inner class SessionBinder : Binder() {
        fun getService():SessionService{
            return this@SessionService
        }
        fun terminateAllSessions(){
            sessions.values.forEach{
                it.finishIfRunning()
            }
            sessions.clear()
            sessionList.clear()
            updateNotification()
        }
        fun createSession(id: String, client: TerminalSessionClient, activity: MainActivity,workingMode:Int): TerminalSession {
            return MkSession.createSession(activity, client, id, workingMode = workingMode).also {
                sessions[id] = it
                sessionList[id] = workingMode
                updateNotification()
                
                // Notify namespace daemon if in shared namespace mode
                if (com.rk.settings.Settings.container_Mode == com.rk.terminal.ui.screens.settings.ContainerMode.CHROOT &&
                    com.rk.settings.Settings.use_unshare &&
                    com.rk.settings.Settings.share_namespace) {
                    // Launch coroutine to register session
                    CoroutineScope(Dispatchers.IO).launch {
                        val prefix = System.getenv("PREFIX") ?: "/data/data/${activity.packageName}/files/usr"
                        NamespaceSessionDaemon.registerSession(prefix)
                    }
                }
            }
        }
        fun getSession(id: String): TerminalSession? {
            return sessions[id]
        }
        fun terminateSession(id: String) {
            runCatching {
                //crash is here
                sessions[id]?.apply {
                    if (emulator != null){
                        sessions[id]?.finishIfRunning()
                    }
                }

                sessions.remove(id)
                sessionList.remove(id)
                
                // Notify namespace daemon
                if (com.rk.settings.Settings.container_Mode == com.rk.terminal.ui.screens.settings.ContainerMode.CHROOT &&
                    com.rk.settings.Settings.use_unshare &&
                    com.rk.settings.Settings.share_namespace) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val prefix = System.getenv("PREFIX") ?: "/data/data/${this@SessionService.packageName}/files/usr"
                        NamespaceSessionDaemon.unregisterSession(prefix)
                    }
                }
                
                if (sessions.isEmpty()) {
                    stopSelf()
                } else {
                    updateNotification()
                }
            }.onFailure { it.printStackTrace() }

        }
    }

    private val binder = SessionBinder()
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        sessions.forEach { s -> s.value.finishIfRunning() }
        
        // Shutdown namespace daemon
        CoroutineScope(Dispatchers.IO).launch {
            NamespaceSessionDaemon.shutdown()
        }
        
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_EXIT" -> {
                sessions.forEach { s -> s.value.finishIfRunning() }
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exitIntent = Intent(this, SessionService::class.java).apply {
            action = "ACTION_EXIT"
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ReTerminal")
            .setContentText(getNotificationContentText())
            .setSmallIcon(drawables.terminal)
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    "EXIT",
                    exitPendingIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    private val CHANNEL_ID = "session_service_channel"

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Session Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for Terminal Service"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val notification = createNotification()
        notificationManager.notify(1, notification)
    }

    private fun getNotificationContentText(): String {
        val count = sessions.size
        if (count == 1){
            return "1 session running"
        }
        return "$count sessions running"
    }
}

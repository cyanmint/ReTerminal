package com.rk.terminal.ui.screens.terminal

import android.os.Environment
import com.rk.libcommons.alpineDir
import com.rk.libcommons.alpineHomeDir
import com.rk.libcommons.application
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.libcommons.localBinDir
import com.rk.libcommons.localDir
import com.rk.libcommons.localLibDir
import com.rk.libcommons.pendingCommand
import com.rk.settings.Settings
import com.rk.terminal.App
import com.rk.terminal.App.Companion.getTempDir
import com.rk.terminal.BuildConfig
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.settings.WorkingMode
import com.rk.terminal.ui.screens.settings.ContainerMode
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import java.io.FileOutputStream

object MkSession {
    fun createSession(
        activity: MainActivity, sessionClient: TerminalSessionClient, session_id: String,workingMode:Int
    ): TerminalSession {
        with(activity) {
            val envVariables = mapOf(
                "ANDROID_ART_ROOT" to System.getenv("ANDROID_ART_ROOT"),
                "ANDROID_DATA" to System.getenv("ANDROID_DATA"),
                "ANDROID_I18N_ROOT" to System.getenv("ANDROID_I18N_ROOT"),
                "ANDROID_ROOT" to System.getenv("ANDROID_ROOT"),
                "ANDROID_RUNTIME_ROOT" to System.getenv("ANDROID_RUNTIME_ROOT"),
                "ANDROID_TZDATA_ROOT" to System.getenv("ANDROID_TZDATA_ROOT"),
                "BOOTCLASSPATH" to System.getenv("BOOTCLASSPATH"),
                "DEX2OATBOOTCLASSPATH" to System.getenv("DEX2OATBOOTCLASSPATH"),
                "EXTERNAL_STORAGE" to System.getenv("EXTERNAL_STORAGE")
            )

            val workingDir = pendingCommand?.workingDir ?: alpineHomeDir().path

            // Determine which init script to use based on container mode and settings
            val containerMode = Settings.container_Mode
            val initScriptName = when {
                containerMode == ContainerMode.PROOT -> "init-host.sh"
                containerMode == ContainerMode.CHROOT && !Settings.use_unshare -> "init-host-chroot-nons.sh"
                // Shared namespace: init always PID 1 (automatic)
                containerMode == ContainerMode.CHROOT && Settings.use_unshare && Settings.share_namespace -> "init-host-chroot-shared.sh"
                // Isolated namespaces: each session gets init as PID 1 (automatic)
                else -> "init-host-chroot.sh" // Default: chroot with unshare, isolated namespaces
            }
            
            val initFile: File = localBinDir().child("init-host")
            val modeFile: File = localBinDir().child(".container-mode")
            
            // Create a unique mode identifier that includes all relevant settings
            val modeIdentifier = "$containerMode:${Settings.use_unshare}:${Settings.share_namespace}:${Settings.use_su}"

            // Check if we need to update the init script
            val shouldUpdate = !initFile.exists() || 
                              !modeFile.exists() || 
                              modeFile.readText() != modeIdentifier

            if (shouldUpdate) {
                initFile.createFileIfNot()
                initFile.writeText(assets.open(initScriptName).bufferedReader().use { it.readText() })
                modeFile.createFileIfNot()
                modeFile.writeText(modeIdentifier)
            }


            localBinDir().child("init").apply {
                if (exists().not()){
                    createFileIfNot()
                    writeText(assets.open("init.sh").bufferedReader().use { it.readText() })
                }
            }


            val env = mutableListOf(
                "PATH=${System.getenv("PATH")}:/sbin:${localBinDir().absolutePath}",
                "HOME=/sdcard",
                "PUBLIC_HOME=${getExternalFilesDir(null)?.absolutePath}",
                "COLORTERM=truecolor",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "BIN=${localBinDir()}",
                "DEBUG=${BuildConfig.DEBUG}",
                "PREFIX=${filesDir.parentFile!!.path}",
                "LD_LIBRARY_PATH=${localLibDir().absolutePath}",
                "LINKER=${if(File("/system/bin/linker64").exists()){"/system/bin/linker64"}else{"/system/bin/linker"}}",
                "NATIVE_LIB_DIR=${applicationInfo.nativeLibraryDir}",
                "PKG=${packageName}",
                "RISH_APPLICATION_ID=${packageName}",
                "PKG_PATH=${applicationInfo.sourceDir}",
                "PROOT_TMP_DIR=${getTempDir().child(session_id).also { if (it.exists().not()){it.mkdirs()} }}",
                "TMPDIR=${getTempDir().absolutePath}"
            )

            if (File(applicationInfo.nativeLibraryDir).child("libproot-loader32.so").exists()){
                env.add("PROOT_LOADER32=${applicationInfo.nativeLibraryDir}/libproot-loader32.so")
            }

            if (File(applicationInfo.nativeLibraryDir).child("libproot-loader.so").exists()){
                env.add("PROOT_LOADER=${applicationInfo.nativeLibraryDir}/libproot-loader.so")
            }

            if (Settings.seccomp) {
                env.add("SECCOMP=1")
            }




            env.addAll(envVariables.map { "${it.key}=${it.value}" })

            localDir().child("stat").apply {
                if (exists().not()){
                    writeText(stat)
                }
            }

            localDir().child("vmstat").apply {
                if (exists().not()){
                    writeText(vmstat)
                }
            }

            pendingCommand?.env?.let {
                env.addAll(it)
            }

            val args: Array<String>

            val shell = if (pendingCommand == null) {
                args = if (workingMode == WorkingMode.ALPINE){
                    arrayOf("-c",initFile.absolutePath)
                }else{
                    arrayOf()
                }
                "/system/bin/sh"
            } else{
                args = pendingCommand!!.args
                pendingCommand!!.shell
            }

            pendingCommand = null
            return TerminalSession(
                shell,
                workingDir,
                args,
                env.toTypedArray(),
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                sessionClient,
            )
        }

    }
}
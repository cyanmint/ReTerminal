package com.rk.terminal.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.components.SettingsToggle
import com.rk.terminal.ui.routes.MainActivityRoutes
import androidx.core.net.toUri


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    title: @Composable () -> Unit,
    description: @Composable () -> Unit = {},
    startWidget: (@Composable () -> Unit)? = null,
    endWidget: (@Composable () -> Unit)? = null,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    PreferenceTemplate(
        modifier = modifier
            .combinedClickable(
                enabled = isEnabled,
                indication = ripple(),
                interactionSource = interactionSource,
                onClick = onClick
            ),
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = title,
        description = description,
        startWidget = startWidget,
        endWidget = endWidget,
        applyPaddings = false
    )

}


object WorkingMode{
    const val ALPINE = 0
    const val ANDROID = 1
    const val CHROOT = 2
}

object UnshareMode{
    const val OWN_NS = 0        // Each session gets its own namespace
    const val FIRST_ONLY = 1    // First session unshares, others nsenter
    const val NO_UNSHARE = 2    // No namespace isolation
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(modifier: Modifier = Modifier,navController: NavController,mainActivity: MainActivity) {
    val context = LocalContext.current
    var selectedOption by remember { mutableIntStateOf(Settings.working_Mode) }

    PreferenceLayout(label = stringResource(strings.settings)) {
        PreferenceGroup(heading = "Default Working mode") {

            SettingsCard(
                title = { Text("Alpine") },
                description = {Text("Alpine Linux")},
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 8.dp),
                        selected = selectedOption == WorkingMode.ALPINE,
                        onClick = {
                            selectedOption = WorkingMode.ALPINE
                            Settings.working_Mode = selectedOption
                        })
                },
                onClick = {
                    selectedOption = WorkingMode.ALPINE
                    Settings.working_Mode = selectedOption
                })


            SettingsCard(
                title = { Text("Android") },
                description = {Text("ReTerminal Android shell")},
                startWidget = {
                    RadioButton(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            ,
                        selected = selectedOption == WorkingMode.ANDROID,
                        onClick = {
                            selectedOption = WorkingMode.ANDROID
                            Settings.working_Mode = selectedOption
                        })
                },
                onClick = {
                    selectedOption = WorkingMode.ANDROID
                    Settings.working_Mode = selectedOption
                })

            SettingsCard(
                title = { Text("Chroot") },
                description = {Text("Alpine Linux with chroot and namespace support")},
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 8.dp),
                        selected = selectedOption == WorkingMode.CHROOT,
                        onClick = {
                            selectedOption = WorkingMode.CHROOT
                            Settings.working_Mode = selectedOption
                        })
                },
                onClick = {
                    selectedOption = WorkingMode.CHROOT
                    Settings.working_Mode = selectedOption
                })
        }


        PreferenceGroup {
            SettingsToggle(
                label = "Customizations",
                showSwitch = false,
                default = false,
                sideEffect = {
                   navController.navigate(MainActivityRoutes.Customization.route)
            }, endWidget = {
                Icon(imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,modifier = Modifier.padding(16.dp))
            })
        }

        // Chroot namespace settings - always visible for configuration
        // These settings apply only to sessions created with CHROOT working mode
        var selectedUnshareMode by remember { mutableIntStateOf(Settings.unshare_mode) }
        
        PreferenceGroup(heading = "Chroot Namespace Mode") {
                SettingsCard(
                    title = { Text("Own namespace") },
                    description = { Text("Each session gets its own namespace") },
                    startWidget = {
                        RadioButton(
                            modifier = Modifier.padding(start = 8.dp),
                            selected = selectedUnshareMode == UnshareMode.OWN_NS,
                            onClick = {
                                selectedUnshareMode = UnshareMode.OWN_NS
                                Settings.unshare_mode = selectedUnshareMode
                            })
                    },
                    onClick = {
                        selectedUnshareMode = UnshareMode.OWN_NS
                        Settings.unshare_mode = selectedUnshareMode
                    })

                SettingsCard(
                    title = { Text("Shared namespace") },
                    description = { Text("First session creates namespace, others join it") },
                    startWidget = {
                        RadioButton(
                            modifier = Modifier.padding(start = 8.dp),
                            selected = selectedUnshareMode == UnshareMode.FIRST_ONLY,
                            onClick = {
                                selectedUnshareMode = UnshareMode.FIRST_ONLY
                                Settings.unshare_mode = selectedUnshareMode
                            })
                    },
                    onClick = {
                        selectedUnshareMode = UnshareMode.FIRST_ONLY
                        Settings.unshare_mode = selectedUnshareMode
                    })

                SettingsCard(
                    title = { Text("No namespace") },
                    description = { Text("Simple chroot without namespace isolation") },
                    startWidget = {
                        RadioButton(
                            modifier = Modifier.padding(start = 8.dp),
                            selected = selectedUnshareMode == UnshareMode.NO_UNSHARE,
                            onClick = {
                                selectedUnshareMode = UnshareMode.NO_UNSHARE
                                Settings.unshare_mode = selectedUnshareMode
                            })
                    },
                    onClick = {
                        selectedUnshareMode = UnshareMode.NO_UNSHARE
                        Settings.unshare_mode = selectedUnshareMode
                    })
        }

        PreferenceGroup {
            SettingsToggle(
                label = "SECCOMP",
                description = "fix operation not permitted error",
                showSwitch = true,
                default = Settings.seccomp,
                sideEffect = {
                    Settings.seccomp = it
                })

            SettingsToggle(
                label = "Debug Output",
                description = "echo commands before executing them",
                showSwitch = true,
                default = Settings.debug_output,
                sideEffect = {
                    Settings.debug_output = it
                })

            var customScriptPath by remember { mutableStateOf(Settings.custom_script_path) }
            
            SettingsCard(
                title = { Text("Custom Init Script") },
                description = { 
                    Text(if (customScriptPath.isEmpty()) 
                        "Using default script - tap to set custom" 
                        else "Custom: ${customScriptPath.substringAfterLast('/')}") 
                },
                onClick = {
                    // For now, show info about placing custom script
                    // In a full implementation, this would open a file picker
                    val defaultPath = "${mainActivity.filesDir}/custom-init.sh"
                    if (customScriptPath.isEmpty()) {
                        customScriptPath = defaultPath
                        Settings.custom_script_path = defaultPath
                    }
                })
            
            if (customScriptPath.isNotEmpty()) {
                SettingsCard(
                    title = { Text("Reset to Default Script") },
                    description = { Text("Remove custom script and use built-in default") },
                    onClick = {
                        customScriptPath = ""
                        Settings.custom_script_path = ""
                    })
            }

            SettingsToggle(
                label = "All file access",
                description = "enable access to /sdcard and /storage",
                showSwitch = false,
                default = false,
                sideEffect = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching {
                            val intent = Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                "package:${context.packageName}".toUri()
                            )
                            context.startActivity(intent)
                        }.onFailure {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    }else{
                        val intent = Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri()
                        )
                        context.startActivity(intent)
                    }

                })

        }
    }
}
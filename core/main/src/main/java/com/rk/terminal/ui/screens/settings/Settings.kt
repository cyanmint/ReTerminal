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
}

object ContainerMode{
    const val PROOT = 0
    const val CHROOT = 1
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(modifier: Modifier = Modifier,navController: NavController,mainActivity: MainActivity) {
    val context = LocalContext.current
    var selectedOption by remember { mutableIntStateOf(Settings.working_Mode) }
    var selectedContainerMode by remember { mutableIntStateOf(Settings.container_Mode) }

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
        }

        PreferenceGroup(heading = "Alpine Container mode") {

            SettingsCard(
                title = { Text("PRoot") },
                description = {Text("Use proot (rootless)")},
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 8.dp),
                        selected = selectedContainerMode == ContainerMode.PROOT,
                        onClick = {
                            selectedContainerMode = ContainerMode.PROOT
                            Settings.container_Mode = selectedContainerMode
                        })
                },
                onClick = {
                    selectedContainerMode = ContainerMode.PROOT
                    Settings.container_Mode = selectedContainerMode
                })

            SettingsCard(
                title = { Text("Chroot") },
                description = {Text("Use chroot & unshare (requires root)")},
                startWidget = {
                    RadioButton(
                        modifier = Modifier.padding(start = 8.dp),
                        selected = selectedContainerMode == ContainerMode.CHROOT,
                        onClick = {
                            selectedContainerMode = ContainerMode.CHROOT
                            Settings.container_Mode = selectedContainerMode
                        })
                },
                onClick = {
                    selectedContainerMode = ContainerMode.CHROOT
                    Settings.container_Mode = selectedContainerMode
                })
        }

        // Common options for container modes
        PreferenceGroup(heading = "Container Options") {
            SettingsToggle(
                label = "Use su (root)",
                description = "Run container commands with root privileges (required for chroot)",
                showSwitch = true,
                default = Settings.use_su,
                sideEffect = {
                    Settings.use_su = it
                })
        }

        // Chroot options - only show when Chroot mode is selected
        if (selectedContainerMode == ContainerMode.CHROOT) {
            PreferenceGroup(heading = "Chroot Options") {
                SettingsToggle(
                    label = "Use unshare",
                    description = "Create isolated namespaces (mount, PID, UTS, IPC)",
                    showSwitch = true,
                    default = Settings.use_unshare,
                    sideEffect = {
                        Settings.use_unshare = it
                        // If unshare is disabled, also disable namespace sharing
                        if (!it) {
                            Settings.share_namespace = false
                        }
                    })

                // Only show namespace sharing option if unshare is enabled
                if (Settings.use_unshare) {
                    SettingsToggle(
                        label = "Share namespace",
                        description = "First session creates namespace, others join it (init always PID 1)",
                        showSwitch = true,
                        default = Settings.share_namespace,
                        sideEffect = {
                            Settings.share_namespace = it
                        })
                }
            }
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
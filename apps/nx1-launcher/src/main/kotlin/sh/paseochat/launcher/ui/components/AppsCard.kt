package sh.paseochat.launcher.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val PREFS_NAME = "daemon"
private const val SHORTCUTS_KEY = "app_shortcuts"
private const val MAX_SHORTCUTS = 4

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val cs = MaterialTheme.colorScheme

    var shortcuts by remember {
        mutableStateOf(
            prefs.getString(SHORTCUTS_KEY, "")
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }
    var editingSlot by remember { mutableStateOf(-1) }
    var showPicker by remember { mutableStateOf(false) }

    fun saveShortcuts(list: List<String>) {
        shortcuts = list
        prefs.edit().putString(SHORTCUTS_KEY, list.joinToString(",")).apply()
    }

    val pm = context.packageManager
    val launchableApps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }

    fun getAppLabel(pkg: String): String {
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    fun launchApp(pkg: String) {
        val intent = pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, cs.onSurface, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHighest),
    ) {
        Box(Modifier.fillMaxHeight()) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                Text(
                    "Apps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                )
                Text(
                    "Quick launch",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(16.dp))

                for (i in 0 until MAX_SHORTCUTS) {
                    val pkg = shortcuts.getOrNull(i)
                    if (pkg != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(cs.surfaceVariant)
                                .combinedClickable(
                                    onClick = { launchApp(pkg) },
                                    onLongClick = {
                                        editingSlot = i
                                        showPicker = true
                                    },
                                )
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                getAppLabel(pkg),
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(cs.surfaceVariant.copy(alpha = 0.5f))
                                .clickable {
                                    editingSlot = i
                                    showPicker = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "\u2014 Add \u2014",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                    if (i < MAX_SHORTCUTS - 1) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            apps = launchableApps,
            onPick = { selectedPkg ->
                val newList = shortcuts.toMutableList()
                when {
                    editingSlot < newList.size -> newList[editingSlot] = selectedPkg
                    newList.size < MAX_SHORTCUTS -> newList.add(selectedPkg)
                }
                saveShortcuts(newList)
                showPicker = false
                editingSlot = -1
            },
            onDismiss = {
                showPicker = false
                editingSlot = -1
            },
            pm = pm,
        )
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<ResolveInfo>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    pm: PackageManager,
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add app") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(scrollState),
            ) {
                apps.forEach { info ->
                    val label = info.loadLabel(pm).toString()
                    val pkg = info.activityInfo.packageName
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(pkg) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

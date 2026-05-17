package com.svllvsx.notifyrelay.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.graphics.drawable.toBitmap
import com.svllvsx.notifyrelay.core.AppResult
import com.svllvsx.notifyrelay.core.AppError
import com.svllvsx.notifyrelay.core.AppContainer
import com.svllvsx.notifyrelay.BuildConfig
import com.svllvsx.notifyrelay.data.db.EventEntity
import com.svllvsx.notifyrelay.data.db.EventStatus
import com.svllvsx.notifyrelay.data.repositories.UpdateInfo
import com.svllvsx.notifyrelay.keepalive.KeepAliveService
import com.svllvsx.notifyrelay.domain.model.AppLanguage
import com.svllvsx.notifyrelay.domain.model.PrivacyMode
import com.svllvsx.notifyrelay.domain.model.InstalledApp
import com.svllvsx.notifyrelay.ui.theme.RelayShape
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class Tab(val icon: ImageVector) {
    Dashboard(Icons.Rounded.CheckCircle), Apps(Icons.Rounded.Apps), Settings(Icons.Rounded.Settings), Diagnostics(Icons.Rounded.Security)
}

@Composable
fun NotifyRelayAppRoot(container: AppContainer, requestSmsPermission: () -> Unit, requestPostNotifications: () -> Unit) {
    val loadedSettings by container.settingsRepository.settings.collectAsState(initial = null)
    val settings = loadedSettings
    if (settings == null) {
        LoadingScreen()
        return
    }
    val s = strings(settings.language)
    var tab by remember { mutableStateOf(Tab.Dashboard) }
    var serverUrl by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl.ifBlank { "http://10.0.2.2:8000" }) }
    var pairingCode by remember { mutableStateOf("") }
    var setupStatus by remember(settings.language) { mutableStateOf(s.setupStatus) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showSmsHelp by remember { mutableStateOf(false) }

    if (!settings.onboardingCompleted) {
        RelayScreen {
            HeroCard(Icons.Rounded.Hub, s.appTitle, s.appSubtitle) { RelayPath(s) }
            SectionCard {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = sanitizeTypedUrl(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(s.serverAddress) },
                    supportingText = { Text(s.serverExample) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                    ),
                    shape = RelayShape.cardSmall,
                )
                TextButton(
                    onClick = { clipboard.getText()?.text?.let { serverUrl = sanitizeTypedUrl(it) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(s.pasteUrl) }
                Button(
                    onClick = {
                        scope.launch {
                            setupStatus = when (val result = container.deviceRepository.checkHealth(serverUrl)) {
                                is AppResult.Success -> s.serverOk
                                is AppResult.Error -> "${s.error}: ${result.type.userMessage(settings.language)}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RelayShape.pill,
                ) { Text(s.checkServer) }
            }
            SectionCard {
                Text(s.pairingCode, style = MaterialTheme.typography.titleMedium)
                Text(s.pairingHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PairingCodeField(value = pairingCode, onValueChange = { pairingCode = it })
                Button(
                    onClick = {
                        scope.launch {
                            setupStatus = when (container.deviceRepository.registerDevice(pairingCode)) {
                                is AppResult.Success -> s.linkedOk
                                is AppResult.Error -> s.linkFailed
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RelayShape.pill,
                ) { Text(s.linkDevice) }
            }
            StatusCard(Icons.Rounded.Security, s.setupStatus, setupStatus, RelayTone.Primary)
            PermissionRow(Icons.Rounded.Notifications, s.notificationAccess, container.permissionsRepository.hasNotificationAccess(), s) {
                context.startActivity(container.permissionsRepository.notificationSettingsIntent())
            }
            PermissionRow(Icons.Rounded.Sms, s.smsAccess, container.permissionsRepository.hasSmsPermission(), s) {
                showSmsHelp = true
            }
            Text(s.tokenNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showSmsHelp) {
            SmsPermissionHelpDialog(s,
                onDismiss = { showSmsHelp = false },
                onOpenSettings = {
                    showSmsHelp = false
                    context.startActivity(container.permissionsRepository.appDetailsSettingsIntent())
                },
                onRequestPermission = {
                    showSmsHelp = false
                    requestSmsPermission()
                },
            )
        }
        return
    }

    RelayScaffold(tab, s, onTab = { tab = it }) { padding ->
        when (tab) {
            Tab.Dashboard -> Dashboard(container, padding, s, requestPostNotifications)
            Tab.Apps -> Apps(container, padding, s)
            Tab.Settings -> Settings(container, padding, requestSmsPermission, s)
            Tab.Diagnostics -> Diagnostics(container, padding, requestSmsPermission, s)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Hub, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
    }
}

private fun sanitizeTypedUrl(value: String): String = value
    .trim()
    .replace('—', '-')
    .replace('–', '-')
    .replace('‑', '-')
    .replace('：', ':')
    .replace('／', '/')

private fun formatPairingCode(value: String): String = value.filter { it.isDigit() }.take(6)

@Composable
private fun PairingCodeField(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(formatPairingCode(it)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium.copy(color = Color.Transparent, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.NumberPassword,
        ),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { index ->
                    val char = value.getOrNull(index)?.toString().orEmpty()
                    val isActive = index == value.length.coerceAtMost(5)
                    val borderColor = when {
                        char.isNotEmpty() -> MaterialTheme.colorScheme.primary
                        isActive -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    Surface(
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RelayShape.cardSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = char.ifEmpty { " " },
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
    )
}

private fun AppError.userMessage(language: AppLanguage): String = if (language.resolveLanguage() == AppLanguage.RU) when (this) {
    AppError.Network -> "нет сети, ошибка DNS/TLS или туннель недоступен с телефона"
    AppError.ServerUnavailable -> "сервер вернул ошибку 5xx"
    AppError.Unauthorized -> "нет авторизации"
    AppError.Forbidden -> "доступ запрещён"
    AppError.InvalidPairingCode -> "неверный код привязки"
    AppError.ExpiredPairingCode -> "код привязки истёк"
    is AppError.Unknown -> message ?: "неизвестная ошибка"
} else when (this) {
    AppError.Network -> "network, DNS/TLS error, or tunnel is unreachable from the phone"
    AppError.ServerUnavailable -> "server returned 5xx"
    AppError.Unauthorized -> "unauthorized"
    AppError.Forbidden -> "forbidden"
    AppError.InvalidPairingCode -> "invalid pairing code"
    AppError.ExpiredPairingCode -> "pairing code expired"
    is AppError.Unknown -> message ?: "unknown error"
}

@Composable
private fun Dashboard(container: AppContainer, padding: PaddingValues, s: UiStrings, requestPostNotifications: () -> Unit) {
    val settings by container.settingsRepository.settings.collectAsState(initial = com.svllvsx.notifyrelay.domain.model.AppSettings())
    val pending by container.eventsRepository.pendingCount().collectAsState(initial = 0)
    val failed by container.eventsRepository.failedCount().collectAsState(initial = 0)
    val sent by container.eventsRepository.sentCount().collectAsState(initial = 0)
    val recentEvents by container.eventsRepository.recentEvents().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(settings.notificationForwardingEnabled, settings.smsForwardingEnabled) {
        KeepAliveService.sync(context, settings.notificationForwardingEnabled || settings.smsForwardingEnabled)
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenTitle(s.relayTitle, s.relaySubtitle, if (settings.lastSyncError == null) s.online else s.issue) }
        item {
            RelayPowerCard(
                enabled = settings.notificationForwardingEnabled,
                batteryOptimized = !container.permissionsRepository.isIgnoringBatteryOptimizations(),
                notificationPermissionMissing = !container.permissionsRepository.hasPostNotificationsPermission(),
                s = s,
                onEnabledChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestPostNotifications()
                    KeepAliveService.sync(context, enabled || settings.smsForwardingEnabled)
                    scope.launch { container.settingsRepository.setNotificationForwarding(enabled) }
                },
                onNotificationPermission = requestPostNotifications,
                onBattery = { context.startActivity(container.permissionsRepository.batteryOptimizationIntent()) },
            )
        }
        item { HeroStatusCard(Icons.Rounded.CheckCircle, s.deviceLinked, s.eventsViaServer, "${s.lastSync}: ${formatSyncTime(settings.lastSyncAt, s)}") }
        item { QueueCard(pending, failed, sent, settings.lastSyncError, s) }
        item { RecentEventsCard(recentEvents, s) }
    }
}

@Composable
private fun Apps(container: AppContainer, padding: PaddingValues, s: UiStrings) {
    val scope = rememberCoroutineScope()
    val apps = remember { container.appsRepository.installedApps() }
    val settings by container.settingsRepository.settings.collectAsState(initial = com.svllvsx.notifyrelay.domain.model.AppSettings())
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(apps, query, settings.showSystemApps) {
        val normalizedQuery = query.trim().lowercase()
        val visibleApps = if (settings.showSystemApps) apps else apps.filterNot { it.isSystem }
        if (normalizedQuery.isBlank()) visibleApps else visibleApps.filter { app ->
            app.label.lowercase().contains(normalizedQuery) || app.packageName.lowercase().contains(normalizedQuery)
        }
    }
    val selected by container.appsRepository.observeSelectedApps().collectAsState(initial = emptyList())
    val selectedMap = selected.associateBy { it.packageName }
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle(s.appsTitle, s.appsSubtitle) }
        item { SearchField(value = query, onValueChange = { query = it }, placeholder = s.searchApp) }
        item { SectionCard { ToggleRow(s.showSystemApps, settings.showSystemApps) { scope.launch { container.settingsRepository.setShowSystemApps(it) } } } }
        if (apps.isEmpty()) {
            item { StatusCard(Icons.Rounded.Apps, s.noApps, s.noAppsDesc, RelayTone.Warning) }
        }
        items(filteredApps, key = { it.packageName }) { app -> AppListItem(app, selectedMap[app.packageName]?.enabled == true) { enabled -> scope.launch { container.appsRepository.setEnabled(app, enabled) } } }
    }
}

@Composable
private fun Settings(container: AppContainer, padding: PaddingValues, requestSmsPermission: () -> Unit, s: UiStrings) {
    val settings by container.settingsRepository.settings.collectAsState(initial = com.svllvsx.notifyrelay.domain.model.AppSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSmsHelp by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateProgress by remember { mutableStateOf(0f) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    fun checkUpdates() {
        scope.launch {
            updateStatus = null
            when (val result = container.updatesRepository.checkLatestRelease()) {
                is AppResult.Success -> {
                    updateInfo = result.data
                    updateStatus = if (result.data.hasUpdate) "${s.updateAvailable}: ${result.data.latestVersion}" else s.appUpToDate
                }
                is AppResult.Error -> updateStatus = s.updateCheckFailed
            }
        }
    }
    LaunchedEffect(Unit) { checkUpdates() }
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenTitle(s.settingsTitle, s.settingsSubtitle) }
        item { SupportProjectCard(s, onTribute = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/tribute/app?startapp=dK9j"))) }, onNowPayments = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://nowpayments.io/donation/svllvsx"))) }, onTelegram = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/svllvsxprod"))) }) }
        item {
            SettingsCard(s.server) {
                CodeLine(settings.serverUrl.ifBlank { s.notSet })
                Button(
                    onClick = { scope.launch { container.deviceRepository.resetDevice() } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RelayShape.pill,
                ) { Text(s.setupAgain) }
            }
        }
        item { SettingsCard(s.forwarding) { ToggleRow(s.notifications, settings.notificationForwardingEnabled) { enabled -> scope.launch { container.settingsRepository.setNotificationForwarding(enabled); KeepAliveService.sync(context, enabled || settings.smsForwardingEnabled) } }; ToggleRow(s.sms, settings.smsForwardingEnabled) { enabled -> scope.launch { container.settingsRepository.setSmsForwarding(enabled); KeepAliveService.sync(context, settings.notificationForwardingEnabled || enabled); if (enabled) requestSmsPermission() } } } }
        item {
            SettingsCard(s.permissions) {
                PermissionInlineRow(Icons.Rounded.Notifications, s.notificationAccess, container.permissionsRepository.hasNotificationAccess(), s) {
                    context.startActivity(container.permissionsRepository.notificationSettingsIntent())
                }
                PermissionInlineRow(Icons.Rounded.Sms, s.smsAccess, container.permissionsRepository.hasSmsPermission(), s) {
                    showSmsHelp = true
                }
            }
        }
        item {
            SettingsCard(s.privacy) {
                Text(settings.privacyMode.description(s), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrivacyMode.entries.forEach { mode ->
                    PrivacyModeRow(
                        mode = mode,
                        selected = settings.privacyMode == mode,
                        s = s,
                        onClick = { scope.launch { container.settingsRepository.setPrivacyMode(mode) } },
                    )
                }
            }
        }
        item {
            SettingsCard(s.language) {
                LanguageRow(AppLanguage.SYSTEM, settings.language == AppLanguage.SYSTEM, s) { scope.launch { container.settingsRepository.setLanguage(AppLanguage.SYSTEM) } }
                LanguageRow(AppLanguage.RU, settings.language == AppLanguage.RU, s) { scope.launch { container.settingsRepository.setLanguage(AppLanguage.RU) } }
                LanguageRow(AppLanguage.EN, settings.language == AppLanguage.EN, s) { scope.launch { container.settingsRepository.setLanguage(AppLanguage.EN) } }
            }
        }
        item {
            UpdatesCard(
                s = s,
                updateInfo = updateInfo,
                status = updateStatus,
                progress = updateProgress,
                downloading = downloadingUpdate,
                onCheck = { checkUpdates() },
                onDownload = {
                    val info = updateInfo ?: return@UpdatesCard
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                        context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                        return@UpdatesCard
                    }
                    scope.launch {
                        downloadingUpdate = true
                        updateProgress = 0f
                        when (val result = container.updatesRepository.downloadApk(info) { updateProgress = it }) {
                            is AppResult.Success -> context.startActivity(container.updatesRepository.installIntent(result.data))
                            is AppResult.Error -> updateStatus = s.updateCheckFailed
                        }
                        downloadingUpdate = false
                    }
                },
                onOpenRelease = { updateInfo?.releaseUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } },
            )
        }
        item { Button(onClick = { scope.launch { container.deviceRepository.resetDevice() } }, modifier = Modifier.fillMaxWidth(), shape = RelayShape.pill) { Text(s.resetDevice) } }
    }
    if (showSmsHelp) {
        SmsPermissionHelpDialog(s,
            onDismiss = { showSmsHelp = false },
            onOpenSettings = {
                showSmsHelp = false
                context.startActivity(container.permissionsRepository.appDetailsSettingsIntent())
            },
            onRequestPermission = {
                showSmsHelp = false
                requestSmsPermission()
            },
        )
    }
}

private fun formatSyncTime(timestamp: Long, s: UiStrings): String = if (timestamp == 0L) s.never else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

@Composable
private fun PrivacyModeRow(mode: PrivacyMode, selected: Boolean, s: UiStrings, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column(Modifier.fillMaxWidth()) {
                Text(mode.title(s), style = MaterialTheme.typography.titleMedium)
                Text(mode.description(s), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RelayShape.cardSmall,
    )
}

private fun PrivacyMode.title(s: UiStrings): String = when (this) {
    PrivacyMode.FULL -> s.fullText
    PrivacyMode.MASKED -> s.masked
    PrivacyMode.MINIMAL -> s.minimal
}

private fun PrivacyMode.description(s: UiStrings): String = when (this) {
    PrivacyMode.FULL -> s.fullTextDesc
    PrivacyMode.MASKED -> s.maskedDesc
    PrivacyMode.MINIMAL -> s.minimalDesc
}

@Composable
private fun LanguageRow(language: AppLanguage, selected: Boolean, s: UiStrings, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(language.title(s), style = MaterialTheme.typography.titleMedium) },
        modifier = Modifier.fillMaxWidth(),
        shape = RelayShape.cardSmall,
    )
}

private fun AppLanguage.title(s: UiStrings): String = when (this) {
    AppLanguage.SYSTEM -> s.systemLanguage
    AppLanguage.RU -> s.russian
    AppLanguage.EN -> s.english
}

@Composable
private fun SmsPermissionHelpDialog(s: UiStrings, onDismiss: () -> Unit, onOpenSettings: () -> Unit, onRequestPermission: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.smsHelpTitle) },
        text = { Text(s.smsHelpText) },
        confirmButton = { Button(onClick = onOpenSettings, shape = RelayShape.pill) { Text(s.openAppInfo) } },
        dismissButton = { TextButton(onClick = onRequestPermission) { Text(s.requestSms) } },
    )
}

@Composable
private fun Diagnostics(container: AppContainer, padding: PaddingValues, requestSmsPermission: () -> Unit, s: UiStrings) {
    val settings by container.settingsRepository.settings.collectAsState(initial = com.svllvsx.notifyrelay.domain.model.AppSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSmsHelp by remember { mutableStateOf(false) }
    var testStatus by remember(s) { mutableStateOf(s.testNotSent) }
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenTitle(s.diagnosticsTitle, s.diagnosticsSubtitle) }
        item { StatusCard(Icons.Rounded.Dns, s.server, settings.serverUrl.ifBlank { s.notSet }, RelayTone.Primary) }
        item { PermissionRow(Icons.Rounded.Notifications, s.notificationAccess, container.permissionsRepository.hasNotificationAccess(), s) { context.startActivity(container.permissionsRepository.notificationSettingsIntent()) } }
        item { PermissionRow(Icons.Rounded.Sms, s.smsAccess, container.permissionsRepository.hasSmsPermission(), s) { showSmsHelp = true } }
        item { StatusCard(Icons.Rounded.CloudOff, s.lastError, settings.lastSyncError ?: s.noErrors, if (settings.lastSyncError == null) RelayTone.Success else RelayTone.Error) }
        item {
            SectionCard {
                Text(s.testMessage, style = MaterialTheme.typography.titleMedium)
                Text(testStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = {
                        scope.launch {
                            testStatus = when (container.deviceRepository.sendTestMessage()) {
                                is AppResult.Success -> s.testAccepted
                                is AppResult.Error -> s.testFailed
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RelayShape.pill,
                ) { Text(s.sendTest) }
            }
        }
    }
    if (showSmsHelp) {
        SmsPermissionHelpDialog(s,
            onDismiss = { showSmsHelp = false },
            onOpenSettings = {
                showSmsHelp = false
                context.startActivity(container.permissionsRepository.appDetailsSettingsIntent())
            },
            onRequestPermission = {
                showSmsHelp = false
                requestSmsPermission()
            },
        )
    }
}

@Composable private fun RelayScreen(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
@Composable private fun RelayScaffold(selected: Tab, s: UiStrings, onTab: (Tab) -> Unit, content: @Composable (PaddingValues) -> Unit) { Scaffold(modifier = Modifier.statusBarsPadding(), containerColor = MaterialTheme.colorScheme.background, bottomBar = { NavigationBar(modifier = Modifier.navigationBarsPadding(), containerColor = MaterialTheme.colorScheme.surfaceContainer) { Tab.entries.forEach { NavigationBarItem(selected = selected == it, onClick = { onTab(it) }, icon = { Icon(it.icon, null) }, label = { Text(it.label(s)) }) } } }, content = content) }
private fun Tab.label(s: UiStrings): String = when (this) { Tab.Dashboard -> s.dashboard; Tab.Apps -> s.apps; Tab.Settings -> s.settings; Tab.Diagnostics -> s.diagnostics }
@Composable private fun ScreenTitle(title: String, subtitle: String, badge: String? = null) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.headlineMedium); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (badge != null) ChipText(badge) } }
@Composable private fun SectionCard(content: @Composable ColumnScope.() -> Unit) { ElevatedCard(shape = RelayShape.cardLarge, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) } }
@Composable private fun HeroCard(icon: ImageVector, title: String, text: String, inner: @Composable () -> Unit) {
    val compact = LocalConfiguration.current.screenHeightDp < 700
    Card(shape = RelayShape.hero, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
        Column(
            Modifier.padding(if (compact) 16.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp),
        ) {
            Icon(icon, null, modifier = Modifier.size(if (compact) 28.dp else 36.dp))
            if (!compact) inner()
            Text(title, style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable private fun RelayPath(s: UiStrings) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { PathNode(Icons.Rounded.Notifications, s.phone); PathDash(); PathNode(Icons.Rounded.Dns, s.server); PathDash(); PathNode(Icons.AutoMirrored.Rounded.Send, "Telegram") } }
@Composable private fun PathNode(icon: ImageVector, label: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null); Text(label, style = MaterialTheme.typography.labelLarge) } }
@Composable private fun PathDash() { Box(Modifier.height(2.dp).width(36.dp).background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f))) }
@Composable private fun ChipText(text: String) { AssistChip(onClick = {}, label = { Text(text) }, shape = RelayShape.pill) }
private enum class RelayTone { Primary, Success, Warning, Error }
@Composable private fun toneColors(tone: RelayTone): Pair<Color, Color> = when (tone) { RelayTone.Primary -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer; RelayTone.Success -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer; RelayTone.Warning -> Color(0xFF5A4520) to Color(0xFFFFDDA6); RelayTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer }
@Composable private fun StatusCard(icon: ImageVector, title: String, text: String, tone: RelayTone) { val colors = toneColors(tone); Card(shape = RelayShape.cardSmall, colors = CardDefaults.cardColors(containerColor = colors.first, contentColor = colors.second), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, modifier = Modifier.size(28.dp)); Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(text, style = MaterialTheme.typography.bodyMedium) } } } }
@Composable private fun PermissionRow(icon: ImageVector, title: String, enabled: Boolean, s: UiStrings, onClick: () -> Unit) { val tone = if (enabled) RelayTone.Success else RelayTone.Warning; SectionCard { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = toneColors(tone).second); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(if (enabled) s.enabled else s.required, color = toneColors(tone).second) }; Button(onClick, shape = RelayShape.pill, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = toneColors(tone).first, contentColor = toneColors(tone).second)) { Text(if (enabled) s.done else s.open) } } } }
@Composable private fun PermissionInlineRow(icon: ImageVector, title: String, enabled: Boolean, s: UiStrings, onClick: () -> Unit) { val tone = if (enabled) RelayTone.Success else RelayTone.Warning; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = toneColors(tone).second); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(if (enabled) s.enabled else s.required, color = toneColors(tone).second) }; Button(onClick, shape = RelayShape.pill, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = toneColors(tone).first, contentColor = toneColors(tone).second)) { Text(if (enabled) s.done else s.open) } } }
@Composable private fun SupportProjectCard(s: UiStrings, onTribute: () -> Unit, onNowPayments: () -> Unit, onTelegram: () -> Unit) { Card(shape = RelayShape.cardSmall, colors = CardDefaults.cardColors(containerColor = Color(0xFF35204A), contentColor = Color(0xFFFFD7F3)), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Favorite, null, modifier = Modifier.size(28.dp)); Column(Modifier.weight(1f)) { Text(s.supportProject, style = MaterialTheme.typography.titleMedium); Text(s.supportProjectDesc, style = MaterialTheme.typography.bodyMedium) } }; Button(onClick = onTribute, modifier = Modifier.fillMaxWidth(), shape = RelayShape.pill, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD7F3), contentColor = Color(0xFF35204A))) { Text(s.supportTributeButton) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { TextButton(onClick = onNowPayments, modifier = Modifier.weight(1f)) { Text(s.supportNowPaymentsButton) }; TextButton(onClick = onTelegram, modifier = Modifier.weight(1f)) { Text(s.telegramChannelButton) } } } } }
@Composable private fun UpdatesCard(s: UiStrings, updateInfo: UpdateInfo?, status: String?, progress: Float, downloading: Boolean, onCheck: () -> Unit, onDownload: () -> Unit, onOpenRelease: () -> Unit) { SettingsCard(s.updates) { Text("${s.currentVersion}: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = MaterialTheme.colorScheme.onSurfaceVariant); if (status != null) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant); if (downloading) { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()); Text("${s.downloadingUpdate}: ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = onCheck, enabled = !downloading, modifier = Modifier.weight(1f), shape = RelayShape.pill) { Text(s.checkUpdates) }; if (updateInfo?.hasUpdate == true && updateInfo.apkUrl != null) Button(onClick = onDownload, enabled = !downloading, modifier = Modifier.weight(1f), shape = RelayShape.pill) { Text(s.downloadAndInstall) } }; if (updateInfo != null) TextButton(onClick = onOpenRelease, enabled = !downloading, modifier = Modifier.fillMaxWidth()) { Text(s.openRelease) } } }
@Composable private fun AppListItem(app: InstalledApp, selected: Boolean, onChecked: (Boolean) -> Unit) { SectionCard { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { AppIcon(app.packageName, app.label); Column(Modifier.weight(1f)) { Text(app.label, style = MaterialTheme.typography.titleMedium); Text(app.packageName, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Switch(selected, onChecked) } } }
@Composable private fun AppIcon(packageName: String, label: String) { val context = LocalContext.current; val bitmap = remember(packageName) { runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull() }; Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer, RelayShape.cardSmall), contentAlignment = Alignment.Center) { if (bitmap != null) Image(bitmap, contentDescription = label, modifier = Modifier.size(34.dp)) else Text(label.take(2).uppercase(), fontWeight = FontWeight.Bold) } }
@Composable private fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Surface(shape = RelayShape.pill, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxWidth()) {
                        if (value.isBlank()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        innerTextField()
                    }
                },
            )
        }
    }
}
@Composable private fun HeroStatusCard(icon: ImageVector, title: String, text: String, detail: String) { val colors = toneColors(RelayTone.Success); Card(shape = RelayShape.hero, colors = CardDefaults.cardColors(containerColor = colors.first, contentColor = colors.second), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { Icon(icon, null, modifier = Modifier.size(34.dp)); Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(text); Text(detail, style = MaterialTheme.typography.labelLarge) } } } }
@Composable private fun QueueCard(pending: Int, failed: Int, sent: Int, error: String?, s: UiStrings) { SectionCard { Text(s.eventQueue, style = MaterialTheme.typography.titleMedium); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric(s.pending, pending.toString(), Modifier.weight(1f)); Metric(s.failed, failed.toString(), Modifier.weight(1f)); Metric(s.sent, sent.toString(), Modifier.weight(1f)); Metric(s.status, if (error == null) s.ok else s.error, Modifier.weight(1f)) } } }
@Composable private fun RelayPowerCard(enabled: Boolean, batteryOptimized: Boolean, notificationPermissionMissing: Boolean, s: UiStrings, onEnabledChange: (Boolean) -> Unit, onNotificationPermission: () -> Unit, onBattery: () -> Unit) { val tone = if (enabled) RelayTone.Success else RelayTone.Warning; val colors = toneColors(tone); Card(shape = RelayShape.cardSmall, colors = CardDefaults.cardColors(containerColor = colors.first, contentColor = colors.second), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Hub, null, modifier = Modifier.size(30.dp)); Column(Modifier.weight(1f)) { Text(if (enabled) s.relayEnabled else s.relayDisabled, style = MaterialTheme.typography.titleMedium); Text(s.keepAliveActive, style = MaterialTheme.typography.bodyMedium) }; Switch(enabled, onEnabledChange) }; if (notificationPermissionMissing) { Text(s.statusNotificationPermission, style = MaterialTheme.typography.bodyMedium); Button(onClick = onNotificationPermission, modifier = Modifier.fillMaxWidth(), shape = RelayShape.pill, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = colors.second, contentColor = colors.first)) { Text(s.notifications) } }; if (batteryOptimized) { Text(s.batteryOptimized, style = MaterialTheme.typography.bodyMedium); Button(onClick = onBattery, modifier = Modifier.fillMaxWidth(), shape = RelayShape.pill, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = colors.second, contentColor = colors.first)) { Text(s.disableBatteryOptimization) } } } } }
@Composable private fun RecentEventsCard(events: List<EventEntity>, s: UiStrings) { SectionCard { Text(s.recentEvents, style = MaterialTheme.typography.titleMedium); if (events.isEmpty()) { Text(s.noRecentEvents, color = MaterialTheme.colorScheme.onSurfaceVariant) } else { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { events.take(20).forEach { RecentEventRow(it) } } } } }
@Composable private fun RecentEventRow(event: EventEntity) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(event.appLabel ?: event.sender ?: event.packageName ?: event.type.uppercase(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(event.bigText?.takeIf { it.isNotBlank() } ?: event.text ?: event.title ?: event.type, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }; StatusDot(event.status) } }
@Composable private fun StatusDot(status: String) { val color = when (status) { EventStatus.SENT -> Color(0xFF52D273); EventStatus.FAILED -> MaterialTheme.colorScheme.error; else -> Color(0xFFFFC857) }; Box(Modifier.size(12.dp).background(color, RelayShape.pill)) }
@Composable private fun Metric(label: String, value: String, modifier: Modifier = Modifier) { val labelSize = when { label.length >= 9 -> 10.sp; label.length >= 7 -> 11.sp; else -> 12.sp }; Surface(modifier = modifier, shape = RelayShape.cardSmall, color = MaterialTheme.colorScheme.surfaceContainerHighest) { Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1); Text(label, fontSize = labelSize, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, textAlign = TextAlign.Center) } } }
@Composable private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) { SectionCard { Text(title, style = MaterialTheme.typography.titleMedium); content() } }
@Composable private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label); Switch(checked, onChecked) } }
@Composable private fun CodeLine(text: String) { Surface(shape = RelayShape.cardSmall, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) { Text(text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)) } }

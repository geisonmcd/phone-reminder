package com.geison.phonereminder.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.geison.phonereminder.ImportPreviewResult
import com.geison.phonereminder.MainViewModel
import com.geison.phonereminder.R
import com.geison.phonereminder.data.MAX_NOTIFICATIONS_PER_DAY
import com.geison.phonereminder.data.NotificationWindowSettings
import com.geison.phonereminder.data.ReminderItem
import com.geison.phonereminder.data.ScheduleSettings
import java.text.DateFormat
import java.time.DayOfWeek
import java.util.Date

private const val NOTIFICATION_TEXT_WARNING_LIMIT = 300
private const val NEW_REMINDER_ID = "new-reminder"

private val AppBackgroundTop = Color(0xFFEAF4FF)
private val AppBackgroundBottom = Color(0xFFF7FBFF)
private val HeroCardTop = Color(0xFF1D4ED8)
private val HeroCardBottom = Color(0xFF0F2F7A)
private val PrimaryCardColor = Color(0xFFFDFEFF)
private val SecondaryCardColor = Color(0xFFF2F7FF)
private val MutedCardColor = Color(0xFFE4EEFB)
private val InkColor = Color(0xFF10203A)
private val AccentColor = Color(0xFF2563EB)
private val SoftAccent = Color(0xFFD8E7FF)
private val PillTextColor = Color(0xFF163E85)
private val AppCardShape = RoundedCornerShape(24.dp)
private val AppPillShape = RoundedCornerShape(999.dp)

private val ReminderColorScheme = lightColorScheme(
    primary = AccentColor,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEAFF),
    onPrimaryContainer = InkColor,
    secondary = Color(0xFF4F7EEA),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F0FF),
    onSecondaryContainer = Color(0xFF173A7A),
    tertiary = Color(0xFF6A8DE3),
    onTertiary = Color.White,
    background = AppBackgroundBottom,
    onBackground = InkColor,
    surface = PrimaryCardColor,
    onSurface = InkColor,
    surfaceVariant = MutedCardColor,
    onSurfaceVariant = Color(0xFF52627E),
    outline = Color(0xFFBDD0F0),
    error = Color(0xFFB42318),
    onError = Color.White,
)

@Composable
fun ReminderApp(
    viewModel: MainViewModel,
    openReminderId: String? = null,
    onOpenReminderHandled: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var reminderFilter by rememberSaveable { mutableStateOf("") }
    var selectedReminderId by rememberSaveable { mutableStateOf<String?>(null) }
    var newReminderCreatedAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var showingConfig by rememberSaveable { mutableStateOf(false) }
    var showingPrivacyPolicy by rememberSaveable { mutableStateOf(false) }
    var configMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<ImportPreviewResult.Ready?>(null) }
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        configMessage = if (uri == null) {
            context.getString(R.string.message_export_canceled)
        } else {
            viewModel.exportReminders(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            pendingImport = null
            configMessage = context.getString(R.string.message_import_canceled)
        } else {
            when (val preview = viewModel.previewImport(uri)) {
                is ImportPreviewResult.Ready -> {
                    configMessage = null
                    pendingImport = preview
                }
                is ImportPreviewResult.Error -> {
                    pendingImport = null
                    configMessage = preview.message
                }
            }
        }
    }

    val isCreatingNewReminder = selectedReminderId == NEW_REMINDER_ID
    val selectedReminder = if (isCreatingNewReminder) {
        ReminderItem(
            id = NEW_REMINDER_ID,
            text = "",
            createdAtEpochMillis = newReminderCreatedAt,
            schedule = ScheduleSettings(),
        )
    } else {
        state.reminders.firstOrNull { it.id == selectedReminderId }
    }
    LaunchedEffect(selectedReminderId, selectedReminder, showingConfig) {
        if (selectedReminderId != null && !isCreatingNewReminder && selectedReminder == null) {
            selectedReminderId = null
        }
        if (selectedReminderId != null) {
            showingConfig = false
            showingPrivacyPolicy = false
        }
    }
    LaunchedEffect(openReminderId, state.reminders) {
        if (openReminderId == null) {
            return@LaunchedEffect
        }

        if (state.reminders.any { it.id == openReminderId }) {
            selectedReminderId = openReminderId
        }
        onOpenReminderHandled()
    }

    MaterialTheme(colorScheme = ReminderColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AppBackgroundTop, AppBackgroundBottom),
                    ),
                ),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent,
            ) {
                if (selectedReminder != null) {
                    BackHandler {
                        selectedReminderId = null
                    }
                    ReminderEditScreen(
                        reminder = selectedReminder,
                        isNewReminder = isCreatingNewReminder,
                        notificationWindow = state.notificationWindow,
                        reminderDays = state.reminderDays,
                        onBack = { selectedReminderId = null },
                        onSave = { text, notificationsPerWeek, notificationsPerDay ->
                            if (isCreatingNewReminder) {
                                if (
                                    viewModel.addReminder(
                                        text = text,
                                        notificationsPerWeek = notificationsPerWeek,
                                        notificationsPerDay = notificationsPerDay,
                                        createdAtEpochMillis = selectedReminder.createdAtEpochMillis,
                                    ) != null
                                ) {
                                    selectedReminderId = null
                                }
                            } else {
                                viewModel.saveReminder(
                                    reminderId = selectedReminder.id,
                                    text = text,
                                    notificationsPerWeek = notificationsPerWeek,
                                    notificationsPerDay = notificationsPerDay,
                                )
                                selectedReminderId = null
                            }
                        },
                        onDelete = {
                            if (!isCreatingNewReminder) {
                                viewModel.deleteReminder(selectedReminder.id)
                                selectedReminderId = null
                            }
                        },
                        onTestNotification = { reminderId, text ->
                            viewModel.testReminder(reminderId, text)
                        },
                    )
                } else if (showingConfig) {
                    BackHandler {
                        showingConfig = false
                    }
                    ConfigScreen(
                        notificationWindow = state.notificationWindow,
                        reminderDays = state.reminderDays,
                        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                        message = configMessage,
                        onBack = { showingConfig = false },
                        onStartHourChange = { startHour ->
                            viewModel.updateNotificationWindow(
                                startHour = startHour,
                                endHour = state.notificationWindow.endHour,
                            )
                        },
                        onEndHourChange = { endHour ->
                            viewModel.updateNotificationWindow(
                                startHour = state.notificationWindow.startHour,
                                endHour = endHour,
                            )
                        },
                        onReminderDayChange = viewModel::updateReminderDay,
                        onExport = {
                            exportLauncher.launch(context.getString(R.string.file_name_export))
                        },
                        onImport = {
                            importLauncher.launch(arrayOf("text/plain"))
                        },
                        onPrivacyPolicy = {
                            showingConfig = false
                            showingPrivacyPolicy = true
                        },
                        onOpenNotificationSettings = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        },
                    )
                } else if (showingPrivacyPolicy) {
                    BackHandler {
                        showingPrivacyPolicy = false
                    }
                    PrivacyPolicyScreen(
                        onBack = { showingPrivacyPolicy = false },
                    )
                } else {
                    HomeScreen(
                        reminderCount = state.reminders.size,
                        reminders = state.reminders,
                        reminderFilter = reminderFilter,
                        onFilterChange = { reminderFilter = it },
                        onConfig = {
                            configMessage = null
                            showingConfig = true
                        },
                        onNewReminder = {
                            newReminderCreatedAt = System.currentTimeMillis()
                            selectedReminderId = NEW_REMINDER_ID
                        },
                        onEdit = { reminderId -> selectedReminderId = reminderId },
                    )
                }
            }

            pendingImport?.let { importPreview ->
                AlertDialog(
                    onDismissRequest = { pendingImport = null },
                    title = { Text(stringResource(R.string.dialog_import_replace_title)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.dialog_import_replace_body,
                                importPreview.currentReminderCount,
                                importPreview.importedState.reminders.size,
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                configMessage = viewModel.importReminders(importPreview.importedState)
                                pendingImport = null
                            },
                        ) {
                            Text(stringResource(R.string.action_replace_reminders))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingImport = null }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    reminderCount: Int,
    reminders: List<ReminderItem>,
    reminderFilter: String,
    onFilterChange: (String) -> Unit,
    onConfig: () -> Unit,
    onNewReminder: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val filteredReminders = reminders
        .sortedByDescending { it.createdAtEpochMillis }
        .filter { reminder ->
            reminder.text.contains(reminderFilter.trim(), ignoreCase = true)
        }

    AppScaffold(
        title = stringResource(R.string.screen_title_home),
        topBarAction = {
            IconButton(onClick = onConfig) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.action_open_settings),
                    tint = Color.White,
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewReminder,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.action_add_reminder),
                )
            }
        },
    ) {
        item {
            ReminderListCard(
                reminderCount = reminderCount,
                filteredCount = filteredReminders.size,
                isFiltering = reminderFilter.isNotBlank(),
                reminderFilter = reminderFilter,
                onFilterChange = onFilterChange,
                content = {
                    when {
                        reminders.isEmpty() -> {
                            ReminderListEmptyState(
                                title = stringResource(R.string.empty_no_reminders_title),
                                body = stringResource(R.string.empty_no_reminders_body),
                            )
                        }
                        filteredReminders.isEmpty() -> {
                            ReminderListEmptyState(
                                title = stringResource(R.string.empty_no_matches_title),
                                body = stringResource(R.string.empty_no_matches_body),
                            )
                        }
                        else -> {
                            filteredReminders.forEachIndexed { index, reminder ->
                                ReminderListItem(
                                    reminder = reminder,
                                    zebraIndex = index,
                                    onEdit = { onEdit(reminder.id) },
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ConfigScreen(
    notificationWindow: NotificationWindowSettings,
    reminderDays: Set<DayOfWeek>,
    notificationsEnabled: Boolean,
    message: String?,
    onBack: () -> Unit,
    onStartHourChange: (Int) -> Unit,
    onEndHourChange: (Int) -> Unit,
    onReminderDayChange: (DayOfWeek, Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    AppScaffold(
        title = stringResource(R.string.screen_title_config),
        navigationAction = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back_to_reminders),
                    tint = Color.White,
                )
            }
        },
    ) {
        if (!message.isNullOrBlank()) {
            item {
                MessageCard(message = message)
            }
        }

        item {
            AppCard(containerColor = SecondaryCardColor) {
                Text(
                    text = stringResource(R.string.config_default_hours_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.config_default_hours_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                StepperRow(
                    label = stringResource(R.string.label_start_hour),
                    value = hourLabel(notificationWindow.startHour),
                    onDecrease = {
                        onStartHourChange((notificationWindow.startHour - 1).coerceIn(0, notificationWindow.endHour - 1))
                    },
                    onIncrease = {
                        onStartHourChange((notificationWindow.startHour + 1).coerceIn(0, notificationWindow.endHour - 1))
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
                StepperRow(
                    label = stringResource(R.string.label_end_hour),
                    value = hourLabel(notificationWindow.endHour),
                    onDecrease = {
                        onEndHourChange((notificationWindow.endHour - 1).coerceIn(notificationWindow.startHour + 1, 23))
                    },
                    onIncrease = {
                        onEndHourChange((notificationWindow.endHour + 1).coerceIn(notificationWindow.startHour + 1, 23))
                    },
                )
            }
        }

        item {
            ReminderDaysCard(
                reminderDays = reminderDays,
                onReminderDayChange = onReminderDayChange,
            )
        }

        item {
            AppCard(containerColor = if (notificationsEnabled) PrimaryCardColor else MutedCardColor) {
                Text(
                    text = stringResource(R.string.config_notifications_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (notificationsEnabled) {
                        stringResource(R.string.config_notifications_enabled)
                    } else {
                        stringResource(R.string.config_notifications_disabled)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!notificationsEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenNotificationSettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(stringResource(R.string.action_open_notification_settings))
                    }
                }
            }
        }

        item {
            BackupActionsCard(
                onExport = onExport,
                onImport = onImport,
            )
        }

        item {
            ConfigActionCard(
                title = stringResource(R.string.config_privacy_title),
                body = stringResource(R.string.config_privacy_body),
                buttonLabel = stringResource(R.string.action_open_privacy_policy),
                onClick = onPrivacyPolicy,
            )
        }
    }
}

@Composable
private fun ReminderDaysCard(
    reminderDays: Set<DayOfWeek>,
    onReminderDayChange: (DayOfWeek, Boolean) -> Unit,
) {
    AppCard(containerColor = PrimaryCardColor) {
        Text(
            text = stringResource(R.string.config_reminder_days_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.config_reminder_days_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        reminderDayOptions().forEachIndexed { index, option ->
            ReminderDayCheckboxRow(
                option = option,
                checked = option.dayOfWeek in reminderDays,
                onCheckedChange = { isChecked ->
                    onReminderDayChange(option.dayOfWeek, isChecked)
                },
            )
            if (index < DayOfWeek.values().lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                )
            }
        }
    }
}

@Composable
private fun ReminderDayCheckboxRow(
    option: ReminderDayOption,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PrivacyPolicyScreen(
    onBack: () -> Unit,
) {
    AppScaffold(
        title = stringResource(R.string.screen_title_privacy_policy),
        navigationAction = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back_to_config),
                    tint = Color.White,
                )
            }
        },
    ) {
        item {
            ScreenIntroCard(
                body = stringResource(R.string.privacy_intro),
                buttonLabel = stringResource(R.string.action_back_to_config),
                onButtonClick = onBack,
            )
        }

        item {
            AppCard(containerColor = PrimaryCardColor) {
                PrivacySection(
                    title = stringResource(R.string.privacy_stores_title),
                    body = stringResource(R.string.privacy_stores_body),
                )
                CompactDivider()
                PrivacySection(
                    title = stringResource(R.string.privacy_notifications_title),
                    body = stringResource(R.string.privacy_notifications_body),
                )
                CompactDivider()
                PrivacySection(
                    title = stringResource(R.string.privacy_sharing_title),
                    body = stringResource(R.string.privacy_sharing_body),
                )
                CompactDivider()
                PrivacySection(
                    title = stringResource(R.string.privacy_backups_title),
                    body = stringResource(R.string.privacy_backups_body),
                )
                CompactDivider()
                PrivacySection(
                    title = stringResource(R.string.privacy_contact_title),
                    body = stringResource(R.string.privacy_contact_body),
                )
            }
        }
    }
}

@Composable
private fun ReminderEditScreen(
    reminder: ReminderItem,
    isNewReminder: Boolean,
    notificationWindow: NotificationWindowSettings,
    reminderDays: Set<DayOfWeek>,
    onBack: () -> Unit,
    onSave: (String, Int, Int) -> Unit,
    onDelete: () -> Unit,
    onTestNotification: (String, String) -> Unit,
) {
    var text by rememberSaveable(reminder.id, reminder.text) { mutableStateOf(reminder.text) }
    var notificationsPerWeek by rememberSaveable(reminder.id, reminder.schedule.notificationsPerWeek) {
        mutableStateOf(reminder.schedule.notificationsPerWeek)
    }
    var notificationsPerDay by rememberSaveable(reminder.id, reminder.schedule.notificationsPerDay) {
        mutableStateOf(reminder.schedule.notificationsPerDay)
    }
    var showDeleteConfirmation by rememberSaveable(reminder.id) { mutableStateOf(false) }
    val isNotificationLengthWarning = text.length > NOTIFICATION_TEXT_WARNING_LIMIT

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    AppScaffold(
        title = if (isNewReminder) {
            stringResource(R.string.screen_title_new_reminder)
        } else {
            stringResource(R.string.screen_title_edit_reminder)
        },
        navigationAction = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back_without_saving),
                    tint = Color.White,
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSave(text, notificationsPerWeek, notificationsPerDay)
                    }
                },
                containerColor = if (text.isBlank()) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (text.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Save,
                    contentDescription = stringResource(R.string.action_save_reminder),
                )
            }
        },
    ) {
        item {
            AppCard(containerColor = PrimaryCardColor) {
                Text(
                    text = stringResource(R.string.label_reminder_text),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    label = { Text(stringResource(R.string.label_reminder)) },
                    colors = appTextFieldColors(),
                    supportingText = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (text.isBlank()) {
                                Text(
                                    text = stringResource(R.string.reminder_text_required),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Text(stringResource(R.string.example_reminder))
                            Text(
                                text = stringResource(
                                    R.string.notification_length_count,
                                    text.length,
                                    NOTIFICATION_TEXT_WARNING_LIMIT,
                                ),
                                color = if (isNotificationLengthWarning) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = stringResource(R.string.notification_length_body),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }

        item {
            AppCard(containerColor = SecondaryCardColor) {
                Text(
                    text = stringResource(R.string.schedule_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.schedule_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                StepperRow(
                    label = stringResource(R.string.label_notifications_per_week),
                    value = notificationsPerWeek.toString(),
                    onDecrease = {
                        notificationsPerWeek = (notificationsPerWeek - notificationsPerDay).coerceAtLeast(notificationsPerDay)
                    },
                    onIncrease = {
                        notificationsPerWeek = (notificationsPerWeek + notificationsPerDay).coerceAtMost(notificationsPerDay * 7)
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
                StepperRow(
                    label = stringResource(R.string.label_notifications_per_day),
                    value = notificationsPerDay.toString(),
                    onDecrease = {
                        val updatedPerDay = (notificationsPerDay - 1).coerceAtLeast(1)
                        notificationsPerDay = updatedPerDay
                        notificationsPerWeek = snapWeeklyCount(notificationsPerWeek, updatedPerDay)
                    },
                    onIncrease = {
                        val updatedPerDay = (notificationsPerDay + 1).coerceAtMost(MAX_NOTIFICATIONS_PER_DAY)
                        notificationsPerDay = updatedPerDay
                        notificationsPerWeek = snapWeeklyCount(notificationsPerWeek, updatedPerDay)
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
                Text(
                    text = schedulePreview(
                        notificationsPerWeek = notificationsPerWeek,
                        notificationsPerDay = notificationsPerDay,
                        startHour = notificationWindow.startHour,
                        endHour = notificationWindow.endHour,
                        reminderDays = reminderDays,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!isNewReminder) {
            item {
                AppCard(containerColor = PrimaryCardColor) {
                    Text(
                        text = stringResource(R.string.actions_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { onTestNotification(reminder.id, text) },
                            enabled = text.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Text(stringResource(R.string.action_test_notification))
                        }
                        IconButton(
                            onClick = { showDeleteConfirmation = true },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete_reminder),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        item {
            AppCard(containerColor = PrimaryCardColor) {
                Text(
                    text = stringResource(R.string.info_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.created_at,
                        formatCreatedAt(reminder.createdAtEpochMillis),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScreenIntroCard(
    body: String,
    buttonLabel: String,
    onButtonClick: () -> Unit,
) {
    AppCard(containerColor = SecondaryCardColor) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onButtonClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(buttonLabel)
        }
    }
}

@Composable
private fun ConfigActionCard(
    title: String,
    body: String,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    AppCard(containerColor = PrimaryCardColor) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(buttonLabel)
        }
    }
}

@Composable
private fun BackupActionsCard(
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    AppCard(containerColor = PrimaryCardColor) {
        ConfigActionSection(
            title = stringResource(R.string.config_export_title),
            body = stringResource(R.string.config_export_body),
            buttonLabel = stringResource(R.string.action_export_txt),
            onClick = onExport,
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 14.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        )
        ConfigActionSection(
            title = stringResource(R.string.config_import_title),
            body = stringResource(R.string.config_import_body),
            buttonLabel = stringResource(R.string.action_import_txt),
            onClick = onImport,
        )
    }
}

@Composable
private fun ConfigActionSection(
    title: String,
    body: String,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(buttonLabel)
    }
}

@Composable
private fun ReminderListCard(
    reminderCount: Int,
    filteredCount: Int,
    isFiltering: Boolean,
    reminderFilter: String,
    onFilterChange: (String) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(containerColor = PrimaryCardColor, contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.saved_reminders_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = reminderCountLabel(reminderCount, filteredCount, isFiltering),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = reminderFilter,
            onValueChange = onFilterChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.filter_reminders_label)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                )
            },
            colors = appTextFieldColors(),
        )
        Spacer(modifier = Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun ReminderListItem(
    reminder: ReminderItem,
    zebraIndex: Int,
    onEdit: () -> Unit,
) {
    val rowColor = if (zebraIndex % 2 == 0) {
        Color.White.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor)
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = reminder.text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        InfoPill(text = scheduleSummary(reminder))
    }
}

@Composable
private fun ReminderListEmptyState(
    title: String,
    body: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MessageCard(message: String) {
    AppCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun PrivacySection(
    title: String,
    body: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CompactDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    )
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallActionButton(label = "-", onClick = onDecrease)
            Spacer(modifier = Modifier.width(4.dp))
            SmallActionButton(label = "+", onClick = onIncrease)
        }
    }
}

@Composable
private fun SmallActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = AppPillShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AppScaffold(
    title: String,
    navigationAction: (@Composable () -> Unit)? = null,
    topBarAction: (@Composable () -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            FixedMenuBar(
                title = title,
                navigationAction = navigationAction,
                action = topBarAction,
            )
        },
        floatingActionButton = floatingActionButton,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 20.dp,
                end = 18.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun AppCard(
    containerColor: Color,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = AppCardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            content = content,
        )
    }
}

@Composable
private fun FixedMenuBar(
    title: String,
    navigationAction: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = Color.Transparent,
        shadowElevation = 10.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(HeroCardTop, HeroCardBottom),
                    ),
                )
                .statusBarsPadding()
                .padding(
                    start = if (navigationAction == null) 20.dp else 6.dp,
                    top = 18.dp,
                    end = 20.dp,
                    bottom = 18.dp,
                ),
        ) {
            if (navigationAction != null) {
                Box(
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    navigationAction()
                }
            }
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = if (navigationAction == null) 0.dp else 48.dp)
                    .padding(end = 48.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (action != null) {
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    action()
                }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Box(
        modifier = Modifier
            .clip(AppPillShape)
            .background(SoftAccent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = PillTextColor,
        )
    }
}

@Composable
private fun appTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = Color(0x99FFFFFF),
    unfocusedContainerColor = Color(0x80FFFFFF),
)

private fun hourLabel(hour: Int): String {
    return "%02d:00".format(hour)
}

private fun formatCreatedAt(epochMillis: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return formatter.format(Date(epochMillis))
}

@Composable
private fun reminderDayOptions(): List<ReminderDayOption> {
    return listOf(
        ReminderDayOption(DayOfWeek.MONDAY, stringResource(R.string.weekday_monday)),
        ReminderDayOption(DayOfWeek.TUESDAY, stringResource(R.string.weekday_tuesday)),
        ReminderDayOption(DayOfWeek.WEDNESDAY, stringResource(R.string.weekday_wednesday)),
        ReminderDayOption(DayOfWeek.THURSDAY, stringResource(R.string.weekday_thursday)),
        ReminderDayOption(DayOfWeek.FRIDAY, stringResource(R.string.weekday_friday)),
        ReminderDayOption(DayOfWeek.SATURDAY, stringResource(R.string.weekday_saturday)),
        ReminderDayOption(DayOfWeek.SUNDAY, stringResource(R.string.weekday_sunday)),
    )
}

@Composable
private fun scheduleSummary(reminder: ReminderItem): String {
    return stringResource(
        R.string.schedule_summary,
        reminder.schedule.notificationsPerWeek,
        reminder.schedule.notificationsPerDay,
    )
}

@Composable
private fun schedulePreview(
    notificationsPerWeek: Int,
    notificationsPerDay: Int,
    startHour: Int,
    endHour: Int,
    reminderDays: Set<DayOfWeek>,
): String {
    val daysText = if (reminderDays.size == 7) {
        stringResource(R.string.schedule_summary_all_days)
    } else {
        reminderDayOptions().filter { it.dayOfWeek in reminderDays }.joinToString(", ") { it.label }
    }
    return stringResource(
        R.string.schedule_summary_full,
        notificationsPerWeek,
        notificationsPerDay,
        hourLabel(startHour),
        hourLabel(endHour),
        daysText,
    )
}

@Composable
private fun reminderCountLabel(
    totalCount: Int,
    filteredCount: Int,
    isFiltering: Boolean,
): String {
    return if (isFiltering) {
        stringResource(R.string.reminder_count_filtered, filteredCount, totalCount)
    } else if (totalCount == 1) {
        stringResource(R.string.reminder_count_one)
    } else {
        stringResource(R.string.reminder_count_many, totalCount)
    }
}

private data class ReminderDayOption(
    val dayOfWeek: DayOfWeek,
    val label: String,
)

private fun snapWeeklyCount(
    value: Int,
    notificationsPerDay: Int,
): Int {
    val minValue = notificationsPerDay
    val maxValue = notificationsPerDay * 7
    val coerced = value.coerceIn(minValue, maxValue)
    val remainder = coerced % notificationsPerDay
    return if (remainder == 0) {
        coerced
    } else {
        (coerced + notificationsPerDay - remainder).coerceAtMost(maxValue)
    }
}

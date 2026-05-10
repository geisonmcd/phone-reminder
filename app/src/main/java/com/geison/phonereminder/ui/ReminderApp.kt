package com.geison.phonereminder.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geison.phonereminder.MainViewModel
import com.geison.phonereminder.data.MAX_NOTIFICATIONS_PER_DAY
import com.geison.phonereminder.data.NotificationWindowSettings
import com.geison.phonereminder.data.ReminderItem

private const val NOTIFICATION_TEXT_WARNING_LIMIT = 300

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
    var draftReminder by rememberSaveable { mutableStateOf("") }
    var reminderFilter by rememberSaveable { mutableStateOf("") }
    var selectedReminderId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingConfig by rememberSaveable { mutableStateOf(false) }
    var showingPrivacyPolicy by rememberSaveable { mutableStateOf(false) }
    var configMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedReminderIds by rememberSaveable { mutableStateOf(emptyList<String>()) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        configMessage = if (uri == null) {
            "Export canceled."
        } else {
            viewModel.exportReminders(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        configMessage = if (uri == null) {
            "Import canceled."
        } else {
            viewModel.importReminders(uri)
        }
    }

    val selectedReminder = state.reminders.firstOrNull { it.id == selectedReminderId }
    LaunchedEffect(selectedReminderId, selectedReminder, showingConfig) {
        if (selectedReminderId != null && selectedReminder == null) {
            selectedReminderId = null
        }
        if (selectedReminderId != null) {
            showingConfig = false
            showingPrivacyPolicy = false
        }
    }
    LaunchedEffect(state.reminders) {
        val validIds = state.reminders.map { it.id }.toSet()
        expandedReminderIds = expandedReminderIds.filter { it in validIds }
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
                        onBack = { selectedReminderId = null },
                        onSave = { text, notificationsPerWeek, notificationsPerDay ->
                            viewModel.saveReminder(
                                reminderId = selectedReminder.id,
                                text = text,
                                notificationsPerWeek = notificationsPerWeek,
                                notificationsPerDay = notificationsPerDay,
                            )
                            selectedReminderId = null
                        },
                        onDelete = {
                            viewModel.deleteReminder(selectedReminder.id)
                            selectedReminderId = null
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
                        onExport = {
                            exportLauncher.launch("phone-reminder-export.txt")
                        },
                        onImport = {
                            importLauncher.launch(arrayOf("text/plain"))
                        },
                        onPrivacyPolicy = {
                            showingConfig = false
                            showingPrivacyPolicy = true
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
                        draftReminder = draftReminder,
                        reminderFilter = reminderFilter,
                        expandedReminderIds = expandedReminderIds,
                        onDraftChange = { draftReminder = it },
                        onFilterChange = { reminderFilter = it },
                        onConfig = {
                            configMessage = null
                            showingConfig = true
                        },
                        onAddReminder = {
                            val reminderId = viewModel.addReminder(draftReminder)
                            draftReminder = ""
                            if (reminderId != null) {
                                selectedReminderId = reminderId
                            }
                        },
                        onToggleExpanded = { reminderId ->
                            expandedReminderIds =
                                if (reminderId in expandedReminderIds) {
                                    expandedReminderIds - reminderId
                                } else {
                                    expandedReminderIds + reminderId
                                }
                        },
                        onEdit = { reminderId -> selectedReminderId = reminderId },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    reminderCount: Int,
    reminders: List<ReminderItem>,
    draftReminder: String,
    reminderFilter: String,
    expandedReminderIds: List<String>,
    onDraftChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onConfig: () -> Unit,
    onAddReminder: () -> Unit,
    onToggleExpanded: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val filteredReminders = reminders.filter { reminder ->
        reminder.text.contains(reminderFilter.trim(), ignoreCase = true)
    }
    var showAddReminderDialog by rememberSaveable { mutableStateOf(false) }

    if (showAddReminderDialog) {
        AddReminderDialog(
            value = draftReminder,
            onValueChange = onDraftChange,
            onDismiss = { showAddReminderDialog = false },
            onAddReminder = {
                onAddReminder()
                showAddReminderDialog = false
            },
        )
    }

    AppScaffold(
        title = "Smart Random Reminder",
        topBarAction = {
            IconButton(onClick = onConfig) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Open settings",
                    tint = Color.White,
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddReminderDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add reminder",
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
                                title = "No reminders yet",
                                body = "Create your first reminder.",
                            )
                        }
                        filteredReminders.isEmpty() -> {
                            ReminderListEmptyState(
                                title = "No matches",
                                body = "Try a different filter.",
                            )
                        }
                        else -> {
                            filteredReminders.forEachIndexed { index, reminder ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    )
                                }
                                ReminderListItem(
                                    reminder = reminder,
                                    expanded = reminder.id in expandedReminderIds,
                                    onToggleExpanded = { onToggleExpanded(reminder.id) },
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
    message: String?,
    onBack: () -> Unit,
    onStartHourChange: (Int) -> Unit,
    onEndHourChange: (Int) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onPrivacyPolicy: () -> Unit,
) {
    AppScaffold(
        title = "Config",
        navigationAction = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back to reminders",
                    tint = Color.White,
                )
            }
        },
    ) {
        item {
            ScreenIntroCard(
                body = "Adjust the default notification window and manage local backups.",
                buttonLabel = "Back to reminders",
                onButtonClick = onBack,
            )
        }

        if (!message.isNullOrBlank()) {
            item {
                MessageCard(message = message)
            }
        }

        item {
            AppCard(containerColor = SecondaryCardColor) {
                Text(
                    text = "Default notification hours",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Applies to all reminders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                StepperRow(
                    label = "Start hour",
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
                    label = "End hour",
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
            ConfigActionCard(
                title = "Export reminders",
                body = "Save to a text file.",
                buttonLabel = "Export to txt",
                onClick = onExport,
            )
        }

        item {
            ConfigActionCard(
                title = "Import reminders",
                body = "Replace the list from a text file.",
                buttonLabel = "Import from txt",
                onClick = onImport,
            )
        }

        item {
            ConfigActionCard(
                title = "Privacy policy",
                body = "Read how your reminder data is handled.",
                buttonLabel = "Open privacy policy",
                onClick = onPrivacyPolicy,
            )
        }
    }
}

@Composable
private fun PrivacyPolicyScreen(
    onBack: () -> Unit,
) {
    AppScaffold(
        title = "Privacy policy",
        navigationAction = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back to config",
                    tint = Color.White,
                )
            }
        },
    ) {
        item {
            ScreenIntroCard(
                body = "This app keeps reminder data on your device unless you choose to export it.",
                buttonLabel = "Back to config",
                onButtonClick = onBack,
            )
        }

        item {
            AppCard(containerColor = PrimaryCardColor) {
                PrivacySection(
                    title = "What the app stores",
                    body = "Smart Random Reminder stores reminders and schedule settings on your device."
                )
                CompactDivider()
                PrivacySection(
                    title = "Notifications",
                    body = "The app uses notification and boot permissions so reminders can appear and reschedule after restart."
                )
                CompactDivider()
                PrivacySection(
                    title = "Data sharing",
                    body = "The app does not send reminder content or personal data to our servers."
                )
                CompactDivider()
                PrivacySection(
                    title = "Backups",
                    body = "Exports are saved only when you choose to create a text backup file."
                )
                CompactDivider()
                PrivacySection(
                    title = "Contact",
                    body = "Publisher: Geison Macedo da Silva."
                )
            }
        }
    }
}

@Composable
private fun ReminderEditScreen(
    reminder: ReminderItem,
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
            title = { Text("Delete reminder?") },
            text = { Text("This reminder will be removed permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    AppScaffold(
        title = "Edit reminder",
        navigationAction = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back without saving",
                    tint = Color.White,
                )
            }
        },
    ) {
        item {
            ScreenIntroCard(
                body = "Update the reminder text, tune the schedule, then save when you're done.",
                buttonLabel = "Back without saving",
                onButtonClick = onBack,
            )
        }

        item {
            AppCard(containerColor = PrimaryCardColor) {
                Text(
                    text = "Reminder text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    label = { Text("Reminder") },
                    colors = appTextFieldColors(),
                    supportingText = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Example: Protect your attention.")
                            Text(
                                text = "${text.length}/$NOTIFICATION_TEXT_WARNING_LIMIT characters before notification-length warning",
                                color = if (isNotificationLengthWarning) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = "Long text may be cut off.",
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
                    text = "Schedule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Weekly totals move in daily steps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                StepperRow(
                    label = "Notifications per week",
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
                    label = "Notifications per day",
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
            }
        }

        item {
            AppCard(containerColor = PrimaryCardColor) {
                Text(
                    text = "Actions",
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
                        onClick = { onSave(text, notificationsPerWeek, notificationsPerDay) },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save reminder")
                    }
                    Button(
                        onClick = { onTestNotification(reminder.id, text) },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text("Test notification")
                    }
                    IconButton(
                        onClick = { showDeleteConfirmation = true },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete reminder",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
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
private fun AddReminderDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAddReminder: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a reminder") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("Reminder text") },
                colors = appTextFieldColors(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onAddReminder,
                enabled = value.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
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
                text = "Saved reminders",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            InfoPill(text = reminderCountLabel(reminderCount, filteredCount, isFiltering))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = reminderFilter,
            onValueChange = onFilterChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Filter reminders") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                )
            },
            colors = appTextFieldColors(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        InfoPill(text = reminderCountLabel(reminderCount, filteredCount, isFiltering))
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ReminderListItem(
    reminder: ReminderItem,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEdit: () -> Unit,
) {
    var isExpandable by rememberSaveable(reminder.id, reminder.text) { mutableStateOf(false) }
    val canToggle = expanded || isExpandable

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (canToggle) {
                    Modifier.clickable(onClick = onToggleExpanded)
                } else {
                    Modifier
                },
            )
            .padding(2.dp),
    ) {
        Text(
            text = reminder.text,
            modifier = Modifier.animateContentSize(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) {
                    isExpandable = result.hasVisualOverflow
                }
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        InfoPill(text = scheduleSummary(reminder))
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onEdit) {
                Text("Edit")
            }
        }
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
                .padding(horizontal = 20.dp, vertical = 18.dp),
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

private fun scheduleSummary(reminder: ReminderItem): String {
    return "${reminder.schedule.notificationsPerWeek}/week, ${reminder.schedule.notificationsPerDay}/day"
}

private fun reminderCountLabel(
    totalCount: Int,
    filteredCount: Int,
    isFiltering: Boolean,
): String {
    return if (isFiltering) {
        "$filteredCount/$totalCount shown"
    } else if (totalCount == 1) {
        "1 reminder"
    } else {
        "$totalCount reminders"
    }
}

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

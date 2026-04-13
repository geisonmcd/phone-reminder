package com.geison.phonereminder.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geison.phonereminder.MainViewModel
import com.geison.phonereminder.data.ReminderItem

@Composable
fun ReminderApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draftReminder by rememberSaveable { mutableStateOf("") }
    var selectedReminderId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingConfig by rememberSaveable { mutableStateOf(false) }
    var configMessage by rememberSaveable { mutableStateOf<String?>(null) }

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
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (selectedReminder != null) {
                BackHandler {
                    selectedReminderId = null
                }
                ReminderEditScreen(
                    reminder = selectedReminder,
                    onBack = { selectedReminderId = null },
                    onSave = { text, notificationsPerWeek, startHour, endHour ->
                        viewModel.saveReminder(
                            reminderId = selectedReminder.id,
                            text = text,
                            notificationsPerWeek = notificationsPerWeek,
                            startHour = startHour,
                            endHour = endHour,
                        )
                        selectedReminderId = null
                    },
                    onDelete = {
                        viewModel.deleteReminder(selectedReminder.id)
                        selectedReminderId = null
                    },
                    onTestNotification = { text ->
                        viewModel.testReminder(text)
                    },
                )
            } else if (showingConfig) {
                BackHandler {
                    showingConfig = false
                }
                ConfigScreen(
                    message = configMessage,
                    onBack = { showingConfig = false },
                    onExport = {
                        exportLauncher.launch("phone-reminder-export.txt")
                    },
                    onImport = {
                        importLauncher.launch(arrayOf("text/plain"))
                    },
                )
            } else {
                Scaffold { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "Phone Reminder",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "Create reminders, give each one its own weekly schedule, and let the phone surface them during the week.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        configMessage = null
                                        showingConfig = true
                                    },
                                ) {
                                    Text("Config")
                                }
                            }
                        }

                        item {
                            AddReminderCard(
                                value = draftReminder,
                                onValueChange = { draftReminder = it },
                                onAddReminder = {
                                    val reminderId = viewModel.addReminder(draftReminder)
                                    draftReminder = ""
                                    if (reminderId != null) {
                                        selectedReminderId = reminderId
                                    }
                                },
                            )
                        }

                        item {
                            Text(
                                text = "Saved reminders",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        if (state.reminders.isEmpty()) {
                            item {
                                EmptyStateCard()
                            }
                        } else {
                            items(
                                items = state.reminders,
                                key = { it.id },
                            ) { reminder ->
                                ReminderRow(
                                    reminder = reminder,
                                    onEdit = { selectedReminderId = reminder.id },
                                    onDelete = { viewModel.deleteReminder(reminder.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(
    message: String?,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 0.dp),
                    ) {
                        Text("Back")
                    }
                    Text(
                        text = "Config",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Export all reminders to a text file or import them back later in the same format.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!message.isNullOrBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            item {
                ConfigActionCard(
                    title = "Export reminders",
                    body = "Create a plain text backup file you can read and import later.",
                    buttonLabel = "Export to txt",
                    onClick = onExport,
                )
            }

            item {
                ConfigActionCard(
                    title = "Import reminders",
                    body = "Load reminders from a text export file. This replaces the current reminders with the file contents.",
                    buttonLabel = "Import from txt",
                    onClick = onImport,
                )
            }
        }
    }
}

@Composable
private fun ReminderEditScreen(
    reminder: ReminderItem,
    onBack: () -> Unit,
    onSave: (String, Int, Int, Int) -> Unit,
    onDelete: () -> Unit,
    onTestNotification: (String) -> Unit,
) {
    var text by rememberSaveable(reminder.id, reminder.text) { mutableStateOf(reminder.text) }
    var notificationsPerWeek by rememberSaveable(reminder.id, reminder.schedule.notificationsPerWeek) {
        mutableStateOf(reminder.schedule.notificationsPerWeek)
    }
    var startHour by rememberSaveable(reminder.id, reminder.schedule.startHour) {
        mutableStateOf(reminder.schedule.startHour)
    }
    var endHour by rememberSaveable(reminder.id, reminder.schedule.endHour) {
        mutableStateOf(reminder.schedule.endHour)
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 0.dp),
                    ) {
                        Text("Back")
                    }
                    Text(
                        text = "Edit reminder",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "This screen controls the text and weekly schedule for one reminder.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Reminder text",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            label = { Text("Reminder") },
                            supportingText = { Text("Example: Protect your attention.") },
                        )
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Weekly schedule",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "This reminder gets its own number of notifications per week and its own active hours. It will never appear more than once in the same day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        StepperRow(
                            label = "Notifications per week",
                            value = notificationsPerWeek.toString(),
                            onDecrease = {
                                notificationsPerWeek = (notificationsPerWeek - 1).coerceAtLeast(1)
                            },
                            onIncrease = {
                                notificationsPerWeek = (notificationsPerWeek + 1).coerceAtMost(7)
                            },
                        )
                        HorizontalDivider()
                        StepperRow(
                            label = "Start hour",
                            value = hourLabel(startHour),
                            onDecrease = {
                                startHour = (startHour - 1).coerceIn(0, endHour - 1)
                            },
                            onIncrease = {
                                startHour = (startHour + 1).coerceIn(0, endHour - 1)
                            },
                        )
                        HorizontalDivider()
                        StepperRow(
                            label = "End hour",
                            value = hourLabel(endHour),
                            onDecrease = {
                                endHour = (endHour - 1).coerceIn(startHour + 1, 23)
                            },
                            onIncrease = {
                                endHour = (endHour + 1).coerceIn(startHour + 1, 23)
                            },
                        )
                    }
                }
            }

            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Actions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    onSave(text, notificationsPerWeek, startHour, endHour)
                                },
                                enabled = text.isNotBlank(),
                            ) {
                                Text("Save")
                            }
                            Button(
                                onClick = { onTestNotification(text) },
                                enabled = text.isNotBlank(),
                            ) {
                                Text("Test notification")
                            }
                        }
                        TextButton(onClick = onDelete) {
                            Text("Delete reminder")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddReminderCard(
    value: String,
    onValueChange: (String) -> Unit,
    onAddReminder: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Add a reminder",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("Reminder text") },
                supportingText = { Text("You will set the schedule on the next screen.") },
            )
            Button(
                onClick = onAddReminder,
                enabled = value.isNotBlank(),
            ) {
                Text("Create reminder")
            }
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
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onClick) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: ReminderItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.clickable(onClick = onEdit),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = reminder.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = scheduleSummary(reminder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "No reminders yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Create a reminder, open it, and tune its weekly schedule on the edit screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
                color = MaterialTheme.colorScheme.onSecondaryContainer,
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
    TextButton(onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun hourLabel(hour: Int): String {
    return "%02d:00".format(hour)
}

private fun scheduleSummary(reminder: ReminderItem): String {
    return "${reminder.schedule.notificationsPerWeek} per week, ${hourLabel(reminder.schedule.startHour)}-${hourLabel(reminder.schedule.endHour)}"
}

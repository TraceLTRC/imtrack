package xyz.tracel.imtrack.sessions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xyz.tracel.imtrack.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The `Minutes` label, the preset chips and a digits-only field. Tapping a chip fills the field;
 * a typed value equal to a chip selects that chip.
 */
@Composable
fun MinutesField(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val typed = text.toIntOrNull()
    val label = stringResource(R.string.minutes)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(R.string.minutes)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MinutePresets.forEach { preset ->
                FilterChip(
                    selected = typed == preset,
                    onClick = { onTextChange(preset.toString()) },
                    label = { Text(preset.toString()) },
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { onTextChange(minutesInput(it)) },
            placeholder = { Text("0") },
            suffix = { Text(stringResource(R.string.minutes_suffix)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
        )
    }
}

/**
 * The `Date` label and the Today, Yesterday and Pick… chips. Pick… opens a date picker with future dates
 * disabled; a picked day other than today or yesterday shows on that chip.
 */
@Composable
fun DateField(date: LocalDate, today: LocalDate, onDateChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val label = dayLabel(date, today)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(R.string.date)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = label == DayLabel.Today,
                onClick = { onDateChange(today) },
                label = { Text(stringResource(R.string.day_today)) },
            )
            FilterChip(
                selected = label == DayLabel.Yesterday,
                onClick = { onDateChange(today.minusDays(1)) },
                label = { Text(stringResource(R.string.day_yesterday)) },
            )
            FilterChip(
                selected = label is DayLabel.Date,
                onClick = { picking = true },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.ic_calendar),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                label = {
                    Text(if (label is DayLabel.Date) formatShortDate(label.date) else stringResource(R.string.pick_date))
                },
            )
        }
    }
    if (picking) {
        PastDatePickerDialog(
            initial = date,
            today = today,
            onPick = {
                picking = false
                onDateChange(it)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun FieldLabel(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The Material 3 date picker; days after [today] can't be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastDatePickerDialog(
    initial: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // The picker works in UTC midnight millis, whatever the device's time zone.
    val todayMillis = today.toUtcMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        yearRange = DatePickerDefaults.YearRange.first..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val picked = state.selectedDateMillis
            TextButton(onClick = { picked?.let { onPick(it.utcMillisToDate()) } }, enabled = picked != null) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    ) {
        DatePicker(state)
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.utcMillisToDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

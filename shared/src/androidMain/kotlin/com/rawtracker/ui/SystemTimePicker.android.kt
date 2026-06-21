package com.rawtracker.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberSystemTimePicker(onTimePicked: (hour: Int, minute: Int) -> Unit): SystemTimePicker {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onTimePicked)
    return remember(context) {
        object : SystemTimePicker {
            override fun show(hour: Int, minute: Int) {
                TimePickerDialog(
                    context,
                    { _, h, m -> callback.value(h, m) },
                    hour,
                    minute,
                    DateFormat.is24HourFormat(context)
                ).show()
            }
        }
    }
}

package com.antonchuraev.homesearchchecklist.consent

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Simple consent dialog for EEA/UK users.
 * Shown once on first launch. Accept/Decline only.
 */
@Composable
fun ConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Non-dismissible — user must choose */ },
        title = {
            Text(
                text = "Privacy & Data Usage",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "We use analytics to improve the app and measure ad effectiveness. " +
                    "Your data helps us understand how features are used and optimize your experience.\n\n" +
                    "Do you consent to data collection for analytics and advertising purposes?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("Accept")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Decline")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    )
}

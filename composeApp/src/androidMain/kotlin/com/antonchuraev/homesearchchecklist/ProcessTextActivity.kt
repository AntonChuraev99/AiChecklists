package com.antonchuraev.homesearchchecklist

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.antonchuraev.aichecklists.R

/**
 * Transparent, no-UI trampoline for the system ACTION_PROCESS_TEXT selection-toolbar items.
 *
 * Four manifest `<activity-alias>` entries (one per [ProcessTextMode]) all target this activity.
 * We identify which alias the user tapped via [getComponentName] (returns the alias, not the
 * target), extract the selected text, then relaunch [MainActivity] with an internal action that
 * MainActivity routes into the right in-app flow. This activity itself never draws anything.
 */
class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = (
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT_READONLY)
            )?.toString()?.trim()

        if (text.isNullOrBlank()) {
            // Don't silently swallow the action — tell the user why nothing happened.
            Toast.makeText(this, R.string.process_text_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // getComponentName() returns the alias that was launched, not ProcessTextActivity.
        val mode = ProcessTextContract.modeForAlias(componentName.className)
            ?: ProcessTextMode.CREATE_AI // safe default if an unknown alias somehow launches us

        val relaunch = Intent(this, MainActivity::class.java).apply {
            action = ProcessTextContract.ACTION_PROCESS_TEXT
            putExtra(ProcessTextContract.EXTRA_TEXT, text)
            putExtra(ProcessTextContract.EXTRA_MODE, mode.name)
            // Reuse the existing app task if present (warm start → MainActivity.onNewIntent),
            // otherwise start it fresh (cold start → MainActivity.onCreate). CLEAR_TOP routes to
            // the already-running MainActivity instead of stacking a second one.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(relaunch)
        finish()
    }
}

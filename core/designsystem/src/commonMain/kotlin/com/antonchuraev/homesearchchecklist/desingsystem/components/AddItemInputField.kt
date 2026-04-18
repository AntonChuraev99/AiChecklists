package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.add_item
import aichecklists.core.designsystem.generated.resources.add_item_placeholder
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import org.jetbrains.compose.resources.stringResource

/**
 * Inline input for adding items. Wraps [AppTextField] in multiline mode so long item text
 * wraps to several lines instead of scrolling horizontally. IME Done still submits.
 */
@Composable
fun AddItemInputField(
    text: String,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
    placeholder: String = stringResource(Res.string.add_item_placeholder),
    modifier: Modifier = Modifier
) {
    val isTextNotBlank = text.isNotBlank()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm)
    ) {
        AppTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = placeholder,
            singleLine = false,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { if (isTextNotBlank) onAdd() }
            )
        )

        Surface(
            onClick = onAdd,
            enabled = isTextNotBlank,
            shape = RoundedCornerShape(12.dp),
            color = if (isTextNotBlank) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .height(56.dp)
                .aspectRatio(1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.add_item),
                    tint = if (isTextNotBlank) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

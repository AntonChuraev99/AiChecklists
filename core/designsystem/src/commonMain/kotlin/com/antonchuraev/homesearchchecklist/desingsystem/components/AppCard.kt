package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.LocalIsDarkTheme

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val shape = MaterialTheme.shapes.medium
    val containerModifier = modifier.fillMaxWidth()
    val contentBox: @Composable () -> Unit = {
        Box(modifier = Modifier.padding(AppDimens.CardPadding)) {
            content()
        }
    }

    if (isDark) {
        val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        val colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
        if (onClick != null) {
            OutlinedCard(
                onClick = onClick,
                modifier = containerModifier,
                shape = shape,
                border = border,
                colors = colors,
            ) { contentBox() }
        } else {
            OutlinedCard(
                modifier = containerModifier,
                shape = shape,
                border = border,
                colors = colors,
            ) { contentBox() }
        }
    } else {
        val colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
        val elevation = CardDefaults.cardElevation(
            defaultElevation = AppDimens.CardElevation
        )
        if (onClick != null) {
            Card(
                onClick = onClick,
                modifier = containerModifier,
                shape = shape,
                colors = colors,
                elevation = elevation,
            ) { contentBox() }
        } else {
            Card(
                modifier = containerModifier,
                shape = shape,
                colors = colors,
                elevation = elevation,
            ) { contentBox() }
        }
    }
}

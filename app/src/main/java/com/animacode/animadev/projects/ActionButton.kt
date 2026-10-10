package com.animacode.animadev.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Visible action boundaries and centred labels in every palette and screen. */
@Composable fun ActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(onClick=onClick,enabled=enabled,
        modifier=(if(compact)modifier else modifier.fillMaxWidth()).heightIn(min=48.dp),
        shape=RoundedCornerShape(14.dp),
        border=BorderStroke(1.5.dp,MaterialTheme.colorScheme.outline.copy(alpha=if(enabled)1f else 0.38f)),
        colors=colors,contentPadding=contentPadding) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(textAlign=TextAlign.Center)) {
            content()
        }
    }
}

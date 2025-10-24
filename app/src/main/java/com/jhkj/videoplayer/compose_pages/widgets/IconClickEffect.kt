package com.jhkj.videoplayer.compose_pages.widgets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun CustomIcon(modifier: Modifier, imageVector: ImageVector,normalColor:Color,pressClick:Color,onClick:(()->Unit)? = null) {
    val interactionSource = remember { MutableInteractionSource() }
    val interactions = remember { mutableStateListOf<Interaction>() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> interactions.add(interaction)
                is PressInteraction.Release -> interactions.remove(interaction.press)
                is PressInteraction.Cancel -> interactions.remove(interaction.press)
            }
        }
    }

    val isPressed = interactions.isNotEmpty()

    Icon(
        imageVector = imageVector,
        contentDescription = "",
        tint = if (isPressed) pressClick else normalColor,
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { /* 点击操作 */
                onClick?.invoke()
            }
    )
}


package me.rerere.rikkahub.ui.components.nav

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalNavController

@Composable
fun BackButton(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current
    IconButton(
        onClick = {
            navController.popBackStack()
        },
        modifier = modifier
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.back)
        )
    }
}

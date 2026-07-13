package com.jing.sakura.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jing.sakura.R
import com.jing.sakura.compose.common.AulamaActionButton
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaTvColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvUpdateDialog(
    update: TvUpdate,
    onDownload: () -> Unit,
    onLater: () -> Unit
) {
    val downloadFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 460.dp, max = 620.dp),
            shape = AulamaCardShape,
            color = AulamaTvColors.SurfaceRaised
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_title, update.version),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = AulamaTvColors.TextPrimary
                )
                Text(
                    text = update.notes.ifBlank { stringResource(R.string.update_body) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = AulamaTvColors.TextSecondary,
                    maxLines = 6
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AulamaActionButton(
                        label = stringResource(R.string.update_download),
                        icon = Icons.Default.Download,
                        onClick = onDownload,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(downloadFocus)
                    )
                    AulamaActionButton(
                        label = stringResource(R.string.update_later),
                        onClick = onLater,
                        modifier = Modifier.weight(0.7f),
                        accent = AulamaTvColors.Blue
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        downloadFocus.requestFocus()
    }
}

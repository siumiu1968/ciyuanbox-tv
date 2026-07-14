@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.screen

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.jing.sakura.auth.AulamaAccount
import com.jing.sakura.auth.AuthUiState
import com.jing.sakura.auth.DeviceCode
import com.jing.sakura.compose.common.AulamaActionButton
import com.jing.sakura.compose.common.AulamaAccountAvatar
import com.jing.sakura.compose.common.AulamaAnimeBrandMark
import com.jing.sakura.compose.common.AulamaCardShape
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.TvLanguage
import com.jing.sakura.compose.common.localizedText
import com.jing.sakura.compose.common.aulamaTvBackground

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DeviceLoginScreen(
    state: AuthUiState,
    onRetry: () -> Unit
) {
    if (state is AuthUiState.Checking || state is AuthUiState.RequestingCode) {
        LoginCheckingScreen()
    } else {
        DeviceCodeLoginScreen(state = state, onRetry = onRetry)
    }
}

@Composable
private fun LoginCheckingScreen() {
    val orbit = rememberInfiniteTransition(label = "login-check-orbit")
    val rotation by orbit.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "login-check-rotation"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            AulamaAnimeBrandMark(height = 160.dp)
            Canvas(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { rotationZ = rotation }
            ) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawArc(
                    color = AulamaTvColors.Cyan,
                    startAngle = -90f,
                    sweepAngle = 112f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun DeviceCodeLoginScreen(
    state: AuthUiState,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aulamaTvBackground()
            .padding(horizontal = 72.dp, vertical = 48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(72.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(0.82f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AulamaAnimeBrandMark(height = 56.dp)
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "使用 Aulama ID 登入",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = AulamaTvColors.Cyan
                )
                Text(
                    text = "將你嘅片庫\n帶到大螢幕",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 42.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = AulamaTvColors.TextPrimary
                )
                Text(
                    text = "收藏、觀看進度同個人化推薦會自動同步。",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 26.sp
                    ),
                    color = AulamaTvColors.TextSecondary
                )
                Spacer(Modifier.size(6.dp))
                LoginStep(number = "1", text = "用手機掃描 QR Code")
                LoginStep(number = "2", text = "確認你嘅 Aulama ID")
                LoginStep(number = "3", text = "電視會自動完成登入")
            }

            LoginStatePanel(
                state = state,
                onRetry = onRetry,
                modifier = Modifier.weight(1.18f)
            )
        }
    }
}

@Composable
private fun LoginStep(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(AulamaTvColors.Cyan.copy(alpha = 0.16f), CircleShape)
                .border(1.dp, AulamaTvColors.Cyan.copy(alpha = 0.42f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = AulamaTvColors.Cyan,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
        Text(
            text = text,
            color = AulamaTvColors.TextSecondary,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp)
        )
    }
}

@Composable
private fun LoginStatePanel(
    state: AuthUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val code = when (state) {
        is AuthUiState.Waiting -> state.code
        is AuthUiState.RateLimited -> state.code
        is AuthUiState.Expired -> state.code
        else -> null
    }
    val remaining = when (state) {
        is AuthUiState.Waiting -> state.remainingSeconds
        is AuthUiState.RateLimited -> state.remainingSeconds
        else -> 0L
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AulamaTvColors.Surface.copy(alpha = 0.92f))
            .border(BorderStroke(1.dp, AulamaTvColors.Outline), RoundedCornerShape(14.dp))
            .padding(horizontal = 32.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (code != null) {
            DeviceCodeContent(code, remaining)
        }

        val status: String? = when (state) {
            AuthUiState.Checking,
            AuthUiState.RequestingCode -> null
            is AuthUiState.Waiting -> if (state.pending) "等待你確認登入" else "裝置碼已準備好"
            is AuthUiState.RateLimited -> "每小時最多嘗試 3 次，請於 ${state.retryAfterSeconds} 秒後重試"
            is AuthUiState.Expired -> "裝置碼已過期"
            is AuthUiState.Error -> state.message
            is AuthUiState.Authenticated -> "登入成功"
        }
        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = if (state is AuthUiState.Error || state is AuthUiState.Expired) {
                    AulamaTvColors.Pink
                } else {
                    AulamaTvColors.TextSecondary
                },
                textAlign = TextAlign.Center
            )
        }

        if (state is AuthUiState.Expired || state is AuthUiState.Error || state is AuthUiState.RateLimited) {
            AnimatedLoginButton(
                label = "重新取得裝置碼",
                onClick = onRetry,
                enabled = state !is AuthUiState.RateLimited
            )
        }
    }
}

@Composable
private fun DeviceCodeContent(code: DeviceCode, remainingSeconds: Long) {
    val approvalUrl = remember(code.verificationUri, code.userCode) {
        "${code.verificationUri}?code=${code.userCode}"
    }
    val qrCode = remember(approvalUrl) {
        val matrix = QRCodeWriter().encode(approvalUrl, BarcodeFormat.QR_CODE, 420, 420)
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565).also { bitmap ->
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    bitmap.setPixel(
                        x,
                        y,
                        if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    )
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = qrCode,
            contentDescription = "掃描 QR Code 登入",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(178.dp)
                .background(Color.White, RoundedCornerShape(10.dp))
                .padding(10.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "掃描或輸入裝置碼",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = AulamaTvColors.TextSecondary
            )
            Text(
                text = code.userCode,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                color = AulamaTvColors.TextPrimary,
                maxLines = 1
            )
            Text(
                text = code.verificationUri,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = AulamaTvColors.Cyan,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.tv.material3.Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = AulamaTvColors.Amber,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "%d:%02d 後失效".format(remainingSeconds / 60, remainingSeconds % 60),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    color = AulamaTvColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun AnimatedLoginButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(180),
        label = "login-button-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0.9f,
        animationSpec = tween(180),
        label = "login-button-alpha"
    )
    AulamaActionButton(
        label = label,
        icon = Icons.Default.Refresh,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.hasFocus }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    )
    LaunchedEffect(enabled) {
        if (enabled) focusRequester.requestFocus()
    }
}

@Composable
fun AccountDialog(
    account: AulamaAccount,
    language: TvLanguage,
    isCheckingForUpdate: Boolean,
    currentVersion: String,
    onLanguageChange: (TvLanguage) -> Unit,
    onCheckForUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    val dismissFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(AulamaCardShape)
                .background(AulamaTvColors.SurfaceRaised)
                .border(1.dp, AulamaTvColors.Outline, AulamaCardShape)
                .padding(30.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                AulamaAccountAvatar(
                    account = account,
                    modifier = Modifier
                        .size(72.dp)
                        .border(2.dp, AulamaTvColors.Cyan.copy(alpha = 0.55f), CircleShape)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 27.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = AulamaTvColors.TextPrimary,
                        maxLines = 1
                    )
                    if (account.email.isNotBlank()) {
                        Text(
                            text = account.email,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                            color = AulamaTvColors.TextSecondary,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = account.role,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                        color = AulamaTvColors.Cyan,
                        maxLines = 1
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = localizedText("介面語言"),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = AulamaTvColors.TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AulamaActionButton(
                        label = "繁體中文",
                        icon = Icons.Default.Check.takeIf { language == TvLanguage.Traditional },
                        accent = AulamaTvColors.Cyan,
                        onClick = { onLanguageChange(TvLanguage.Traditional) },
                        modifier = Modifier.weight(1f).height(52.dp)
                    )
                    AulamaActionButton(
                        label = "简体中文",
                        icon = Icons.Default.Check.takeIf { language == TvLanguage.Simplified },
                        accent = AulamaTvColors.Blue,
                        onClick = { onLanguageChange(TvLanguage.Simplified) },
                        modifier = Modifier.weight(1f).height(52.dp)
                    )
                }
            }
            AulamaActionButton(
                label = if (isCheckingForUpdate) {
                    "正在檢查更新"
                } else {
                    "檢查更新 · v$currentVersion"
                },
                icon = Icons.Default.SystemUpdateAlt,
                enabled = !isCheckingForUpdate,
                accent = AulamaTvColors.Green,
                onClick = onCheckForUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                AulamaActionButton(
                    label = "返回",
                    onClick = onDismiss,
                    modifier = Modifier.height(52.dp).focusRequester(dismissFocus)
                )
                AulamaActionButton(
                    label = "登出",
                    icon = Icons.Default.Logout,
                    accent = AulamaTvColors.Pink,
                    onClick = onLogout,
                    modifier = Modifier.height(52.dp)
                )
            }
        }
        LaunchedEffect(Unit) { dismissFocus.requestFocus() }
    }
}

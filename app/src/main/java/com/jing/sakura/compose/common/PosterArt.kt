package com.jing.sakura.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import coil.request.ImageRequest

@Composable
fun rememberPosterImageRequest(
    imageUrl: String,
    widthPx: Int = 420,
    heightPx: Int = 600
): ImageRequest {
    val context = LocalContext.current
    return remember(imageUrl, widthPx, heightPx) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .memoryCacheKey(imageUrl)
            .diskCacheKey(imageUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .size(widthPx, heightPx)
            .build()
    }
}

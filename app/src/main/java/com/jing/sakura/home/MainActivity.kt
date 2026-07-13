package com.jing.sakura.home

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.jing.sakura.R
import com.jing.sakura.compose.screen.HomeScreen
import com.jing.sakura.compose.theme.SakuraTheme
import com.jing.sakura.update.TvUpdate
import com.jing.sakura.update.TvUpdateDialog
import com.jing.sakura.update.TvUpdateManager
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel


class MainActivity : ComponentActivity() {
    private lateinit var updateManager: TvUpdateManager
    private val availableUpdate = mutableStateOf<TvUpdate?>(null)
    private var receiverRegistered = false

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            updateManager.handleDownloadComplete(downloadId)
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateManager = TvUpdateManager(this)
        registerDownloadReceiver()
        val viewModel: HomeViewModel by viewModel()
        setContent {
            SakuraTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    CompositionLocalProvider(
                        androidx.tv.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface
                    ) {
                        HomeScreen(viewModel)
                    }
                    availableUpdate.value?.let { update ->
                        TvUpdateDialog(
                            update = update,
                            onDownload = {
                                updateManager.download(update)
                                availableUpdate.value = null
                                Toast.makeText(
                                    this@MainActivity,
                                    R.string.update_downloading,
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onLater = { availableUpdate.value = null }
                        )
                    }
                }
            }
        }
        lifecycleScope.launch {
            availableUpdate.value = runCatching { updateManager.checkForUpdate() }.getOrNull()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::updateManager.isInitialized) {
            updateManager.installPendingUpdate()
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(downloadReceiver) }
        }
        super.onDestroy()
    }

    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(downloadReceiver, filter)
        }
        receiverRegistered = true
    }
}

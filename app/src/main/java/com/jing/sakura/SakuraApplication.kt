package com.jing.sakura

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.room.Room
import coil.disk.DiskCache
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.jing.sakura.auth.AulamaAuthRepository
import com.jing.sakura.auth.AuthViewModel
import com.jing.sakura.auth.SecureAuthStorage
import com.jing.sakura.detail.DetailPageViewModel
import com.jing.sakura.extend.AndroidCookieJar
import com.jing.sakura.history.HistoryViewModel
import com.jing.sakura.home.CategoryViewModel
import com.jing.sakura.home.HomeViewModel
import com.jing.sakura.http.WebServerContext
import com.jing.sakura.player.VideoPlayerViewModel
import com.jing.sakura.repo.WebPageRepository
import com.jing.sakura.room.SakuraDatabase
import com.jing.sakura.search.SearchResultViewModel
import com.jing.sakura.search.SearchViewModel
import com.jing.sakura.timeline.TimelineViewModel
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.context.startKoin
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class SakuraApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        CookieManager.getInstance().removeAllCookies { }
        context = this
        startKoin {
            androidContext(this@SakuraApplication)
            androidLogger()
            modules(httpModule(), roomModule(), viewModelModule())
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
            private set

        val USER_AGENT by lazy { WebSettings.getDefaultUserAgent(context) }
    }

    private fun httpModule() = module {
        single(qualifier(KoinOkHttpClient.DATA)) { provideOkHttpClient() }
        single(qualifier(KoinOkHttpClient.MEDIA)) {
            basicOkhttpClient()
                .apply {
                    if (BuildConfig.DEBUG) {
                        addNetworkInterceptor(
                            HttpLoggingInterceptor().apply {
                                level = HttpLoggingInterceptor.Level.BASIC
                            })
                    }
                }
                .build()
        }
        single(qualifier(KoinOkHttpClient.AULAMA)) { provideAulamaOkHttpClient() }
        single { SecureAuthStorage(get()) }
        single { AulamaAuthRepository(get(qualifier(KoinOkHttpClient.AULAMA)), get()) }
        single { WebPageRepository(get(qualifier = qualifier(KoinOkHttpClient.DATA))) }
    }

    private fun roomModule() = module {
        single {
            val builder = Room.databaseBuilder(context, SakuraDatabase::class.java, "sk_db")
                .addMigrations(
                    SakuraDatabase.MIGRATION_1_2,
                    SakuraDatabase.MIGRATION_2_3,
                    SakuraDatabase.MIGRATION_3_4
                )
            if (BuildConfig.DEBUG) {
                builder.setQueryCallback({ sqlQuery, bindArgs ->
                    Log.d("RoomQuery", "sql: $sqlQuery, args: $bindArgs")
                }, Executors.newSingleThreadExecutor())
            }
            builder.build()
        }

        single {
            get<SakuraDatabase>().getVideoHistoryDao()
        }

        single {
            get<SakuraDatabase>().searchHistoryDao()
        }
    }

    private fun viewModelModule() = module {
        viewModel { holder -> DetailPageViewModel(holder.get(), get(), get(), holder.get()) }
        viewModel { holder -> VideoPlayerViewModel(holder.get(), get(), get()) }
        viewModelOf(::HomeViewModel)
        viewModelOf(::AuthViewModel)
        viewModel { holder -> SearchViewModel(get(), holder.get()) }
        viewModel { holder -> TimelineViewModel(get(), holder.get()) }
        viewModelOf(::HistoryViewModel)
        viewModel { holder -> SearchResultViewModel(holder.get(), get(), holder.get()) }
        viewModel { holder -> CategoryViewModel(get(), holder.get()) }

    }

    private fun provideOkHttpClient(): OkHttpClient {
        return basicOkhttpClient()
            .cookieJar(AndroidCookieJar())
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.HEADERS
                    })
                }
            }.build()
    }

    private fun provideAulamaOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5L, TimeUnit.SECONDS)
        .readTimeout(15L, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            val request = chain.request()
            require(request.url.isHttps && request.url.host == "aulama.org") {
                "Aulama auth client only accepts https://aulama.org"
            }
            chain.proceed(
                request.newBuilder()
                    .header("User-Agent", "Aulama-Anime-TV/${BuildConfig.VERSION_NAME}")
                    .build()
            )
        }
        .build()

    private fun basicOkhttpClient(): OkHttpClient.Builder {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            }

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslSocketFactory = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }.socketFactory
        return OkHttpClient.Builder()
            .connectTimeout(2L, TimeUnit.SECONDS)
            .readTimeout(20L, TimeUnit.SECONDS)
            .sslSocketFactory(sslSocketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val userAgent = request.header("User-Agent") ?: USER_AGENT
                chain.proceed(
                    request.newBuilder()
                        .header("User-Agent", userAgent)
                        .build()
                )
            })
    }

    override fun onTerminate() {
        WebServerContext.stopServer()
        super.onTerminate()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader(this).newBuilder()
        .okHttpClient(basicOkhttpClient().build())
        .allowHardware(true)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("anime_posters"))
                .maxSizeBytes(256L * 1024L * 1024L)
                .build()
        }
        .build()

    enum class KoinOkHttpClient {
        DATA, MEDIA, AULAMA
    }
}

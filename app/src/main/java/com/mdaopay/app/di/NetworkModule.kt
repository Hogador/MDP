package com.mdaopay.app.di

import com.mdaopay.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

// ponytail: CertificatePinner for backend API domains.
// Replace with actual SHA-256 hashes before release (use `openssl s_client -connect host:port -servername host </dev/null 2>/dev/null | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64`)
// F-024 fix: prevents MITM with rogue CA.
// Audit: skip pinning when CERT_PIN_API is the placeholder (dev builds without real certs)
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val PLACEHOLDER_API = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val PLACEHOLDER_BACKUP = "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="

    private fun hasRealPins(): Boolean =
        BuildConfig.CERT_PIN_API != PLACEHOLDER_API &&
        BuildConfig.CERT_PIN_BACKUP != PLACEHOLDER_BACKUP

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        if (hasRealPins()) {
            val pinner = CertificatePinner.Builder()
                .add("api.mdaopay.com", BuildConfig.CERT_PIN_API)
                .add("mdaopay.com", BuildConfig.CERT_PIN_BACKUP)
                .build()
            builder.certificatePinner(pinner)
        }

        return builder.build()
    }
}

package com.cappielloantonio.tempo.github

import android.content.pm.ApplicationInfo

import com.cappielloantonio.tempo.App
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class GithubRetrofitClient(github: Github) {
    var retrofit: Retrofit

    init {
        retrofit = Retrofit.Builder()
                .baseUrl(github.url)
                .addConverterFactory(GsonConverterFactory.create())
                .client(getOkHttpClient())
                .build()
    }

    private fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
                .addInterceptor(getHttpLoggingInterceptor())
                .build()
    }

    private fun getHttpLoggingInterceptor(): HttpLoggingInterceptor {
        val loggingInterceptor = HttpLoggingInterceptor()

        // Nessuna credenziale qui: BASIC in debug (riga richiesta + codice risposta), silenzio in release.
        val debuggable = App.getContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        loggingInterceptor.setLevel(if (debuggable) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE)

        return loggingInterceptor
    }
}
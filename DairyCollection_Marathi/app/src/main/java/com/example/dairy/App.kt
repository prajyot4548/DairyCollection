package com.example.dairy

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

// Makes the whole app (date picker, dialogs, system buttons) use Marathi
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("mr-IN"))
    }
}

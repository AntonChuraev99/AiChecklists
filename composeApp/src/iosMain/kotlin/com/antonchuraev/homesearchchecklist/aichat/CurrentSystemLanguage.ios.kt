package com.antonchuraev.homesearchchecklist.aichat

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

actual fun currentSystemLanguage(): String = NSLocale.currentLocale.languageCode

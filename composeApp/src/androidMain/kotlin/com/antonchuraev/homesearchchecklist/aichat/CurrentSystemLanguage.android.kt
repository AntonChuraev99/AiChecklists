package com.antonchuraev.homesearchchecklist.aichat

import java.util.Locale

actual fun currentSystemLanguage(): String = Locale.getDefault().language

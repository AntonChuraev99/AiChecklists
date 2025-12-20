package com.antonchuraev.homesearchchecklist.core.common.impl

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger

actual fun createLogger(): AppLogger = IosAppLogger()

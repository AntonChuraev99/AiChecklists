package com.antonchuraev.homesearchchecklist

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
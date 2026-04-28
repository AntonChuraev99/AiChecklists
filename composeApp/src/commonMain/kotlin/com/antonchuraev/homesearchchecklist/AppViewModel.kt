package com.antonchuraev.homesearchchecklist

import androidx.lifecycle.ViewModel

/**
 * App-level ViewModel. Previously held the NavController install bridge;
 * that coupling is removed — navigation is now purely Channel-based via
 * AppNavigator.commands collected in App.kt.
 */
class AppViewModel : ViewModel()

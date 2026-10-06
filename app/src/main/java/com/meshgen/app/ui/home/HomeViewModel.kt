package com.meshgen.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.meshgen.app.device.DeviceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(val device: DeviceProfile)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(HomeUiState(device = DeviceProfile.detect(app)))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
}

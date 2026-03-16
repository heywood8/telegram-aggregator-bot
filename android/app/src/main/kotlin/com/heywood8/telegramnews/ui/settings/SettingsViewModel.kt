package com.heywood8.telegramnews.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val telegramRepo: TelegramRepository,
) : ViewModel() {

    fun logOut() {
        viewModelScope.launch {
            telegramRepo.logOut()
        }
    }
}

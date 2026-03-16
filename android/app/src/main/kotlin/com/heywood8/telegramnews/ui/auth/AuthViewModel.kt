package com.heywood8.telegramnews.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heywood8.telegramnews.domain.model.AuthState
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: TelegramRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = repo.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Unknown)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun sendPhoneNumber(phone: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                repo.sendPhoneNumber(phone)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to send phone number"
            } finally {
                _loading.value = false
            }
        }
    }

    fun sendCode(code: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                repo.sendCode(code)
            } catch (e: Exception) {
                _error.value = e.message ?: "Invalid code"
            } finally {
                _loading.value = false
            }
        }
    }

    fun sendPassword(password: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                repo.sendPassword(password)
            } catch (e: Exception) {
                _error.value = e.message ?: "Invalid password"
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

package com.heywood8.telegramnews.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heywood8.telegramnews.data.local.UserPreferencesRepository
import com.heywood8.telegramnews.domain.model.Channel
import com.heywood8.telegramnews.domain.model.Subscription
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import com.heywood8.telegramnews.domain.usecase.SubscriptionUseCase
import com.heywood8.telegramnews.ui.feed.FeedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val localRepo: LocalRepository,
    private val telegramRepo: TelegramRepository,
    private val subscriptionUseCase: SubscriptionUseCase,
    private val userPrefs: UserPreferencesRepository,
) : ViewModel() {

    val showChannelIcons: StateFlow<Boolean> = userPrefs.showChannelIcons

    val subscriptions: StateFlow<List<Subscription>> = localRepo.observeSubscriptions(FeedViewModel.USER_ID)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _searchResults = MutableStateFlow<List<Channel>>(emptyList())
    val searchResults: StateFlow<List<Channel>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Selected subscription for the settings sheet — derived from subscriptions so keywords stay live
    private val _selectedChannel = MutableStateFlow<String?>(null)
    val selectedSub: StateFlow<Subscription?> = combine(_selectedChannel, subscriptions) { channel, subs ->
        channel?.let { subs.find { it.channel == channel } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        val handler = CoroutineExceptionHandler { _, e ->
            _error.value = e.message
            _searching.value = false
        }
        viewModelScope.launch(handler) {
            _searching.value = true
            _error.value = null
            try {
                _searchResults.value = telegramRepo.searchChannel(query)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _searching.value = false
            }
        }
    }

    fun subscribe(channel: String) {
        viewModelScope.launch {
            subscriptionUseCase.addSubscription(FeedViewModel.USER_ID, channel)
            _searchResults.value = emptyList()
        }
    }

    fun unsubscribe(channel: String) {
        viewModelScope.launch {
            subscriptionUseCase.removeSubscription(FeedViewModel.USER_ID, channel)
            _selectedChannel.value = null
        }
    }

    fun setMode(channel: String, mode: String) {
        viewModelScope.launch {
            subscriptionUseCase.setMode(FeedViewModel.USER_ID, channel, mode)
        }
    }

    fun setIncludePhotos(channel: String, value: Boolean) {
        viewModelScope.launch {
            localRepo.setIncludePhotos(FeedViewModel.USER_ID, channel, value)
        }
    }

    fun addKeyword(channel: String, keyword: String) {
        viewModelScope.launch {
            subscriptionUseCase.addKeyword(FeedViewModel.USER_ID, channel, keyword)
        }
    }

    fun removeKeyword(channel: String, keyword: String) {
        viewModelScope.launch {
            subscriptionUseCase.removeKeyword(FeedViewModel.USER_ID, channel, keyword)
        }
    }

    fun selectSubscription(sub: Subscription?) {
        _selectedChannel.value = sub?.channel
    }

    fun clearError() {
        _error.value = null
    }
}

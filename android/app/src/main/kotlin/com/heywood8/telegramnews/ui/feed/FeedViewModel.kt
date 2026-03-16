package com.heywood8.telegramnews.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heywood8.telegramnews.domain.model.Message
import com.heywood8.telegramnews.domain.model.Subscription
import com.heywood8.telegramnews.domain.repository.LocalRepository
import com.heywood8.telegramnews.domain.repository.TelegramRepository
import com.heywood8.telegramnews.domain.usecase.FilterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val localRepo: LocalRepository,
    private val telegramRepo: TelegramRepository,
    private val filterUseCase: FilterUseCase,
) : ViewModel() {

    companion object {
        const val USER_ID = 0L
    }

    val subscriptions: StateFlow<List<Subscription>> = localRepo.observeSubscriptions(USER_ID)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    private val _selectedChannel = MutableStateFlow<String?>(null)
    val selectedChannel: StateFlow<String?> = _selectedChannel.asStateFlow()

    val filteredMessages: StateFlow<List<Message>> = combine(_messages, _selectedChannel) { msgs, channel ->
        if (channel == null) msgs else msgs.filter { it.channel == channel }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            localRepo.observeSubscriptions(USER_ID)
                .flatMapLatest { subs ->
                    val channels = subs.filter { it.active }.map { it.channel }
                    telegramRepo.observeNewMessages(channels)
                        .filter { msg ->
                            val sub = subs.find { it.channel == msg.channel }
                            sub != null && filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
                        }
                }
                .collect { msg ->
                    _messages.update { existing ->
                        (listOf(msg) + existing).sortedByDescending { it.timestamp }
                    }
                }
        }
    }

    fun selectChannel(channel: String?) {
        _selectedChannel.value = channel
    }
}

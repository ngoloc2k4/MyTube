package vn.lobie.mytube.ui.subscriptions

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import vn.lobie.mytube.data.local.db.MyTubeDatabase
import vn.lobie.mytube.data.local.db.entity.SubscriptionEntity
import vn.lobie.mytube.domain.model.SearchResult
import vn.lobie.mytube.domain.model.Video
import vn.lobie.mytube.domain.repository.YouTubeRepository

class SubscriptionsViewModel(
    application: Application,
    private val repository: YouTubeRepository
) : AndroidViewModel(application) {

    private val db = MyTubeDatabase.getInstance(application)
    private val subscriptionDao = db.subscriptionDao()

    val subscriptions: StateFlow<List<SubscriptionEntity>> = subscriptionDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedChannel = MutableStateFlow<SubscriptionEntity?>(null)
    val selectedChannel: StateFlow<SubscriptionEntity?> = _selectedChannel.asStateFlow()

    private val _feedVideos = MutableStateFlow<List<Video>>(emptyList())
    val feedVideos: StateFlow<List<Video>> = _feedVideos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            subscriptions.collect { subs ->
                if (subs.isNotEmpty()) {
                    loadFeedForSubscriptions(subs, _selectedChannel.value)
                } else {
                    _feedVideos.value = emptyList()
                    _selectedChannel.value = null
                }
            }
        }
    }

    fun selectChannel(channel: SubscriptionEntity?) {
        _selectedChannel.value = channel
        loadFeedForSubscriptions(subscriptions.value, channel)
    }

    fun unsubscribe(channelId: String) {
        viewModelScope.launch {
            subscriptionDao.delete(channelId)
            if (_selectedChannel.value?.channelId == channelId) {
                _selectedChannel.value = null
            }
        }
    }

    fun refresh() {
        loadFeedForSubscriptions(subscriptions.value, _selectedChannel.value)
    }

    private fun loadFeedForSubscriptions(
        subs: List<SubscriptionEntity>,
        selected: SubscriptionEntity?
    ) {
        if (subs.isEmpty()) {
            _feedVideos.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val targets = if (selected != null) listOf(selected) else subs.take(6)
                val deferredVideos = targets.map { sub ->
                    async {
                        val query = sub.channelName
                        repository.search(query).getOrNull()?.mapNotNull { item ->
                            if (item is SearchResult.VideoItem) item.video else null
                        } ?: emptyList()
                    }
                }

                val allResults = deferredVideos.awaitAll().flatten()
                // Strict deduplication
                val deduplicated = allResults.distinctBy { it.id }

                _feedVideos.value = deduplicated
            } catch (e: Exception) {
                Log.e("SubscriptionsVM", "Failed to load channel feed", e)
                _error.value = e.localizedMessage ?: "Failed to load feed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val backupManager = vn.lobie.mytube.data.importer.BackupRestoreManager(getApplication(), db)
    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    fun clearImportStatus() {
        _importStatus.value = null
    }

    fun importSubscriptionsFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            val result = backupManager.importSubscriptionsFromFile(uri)
            _importStatus.value = result.fold(
                onSuccess = { count -> "Đã nhập thành công $count kênh đăng ký!" },
                onFailure = { err -> "Nhập kênh thất bại: ${err.message}" }
            )
        }
    }
}

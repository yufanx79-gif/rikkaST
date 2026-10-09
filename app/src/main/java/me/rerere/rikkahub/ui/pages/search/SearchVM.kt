package me.rerere.rikkahub.ui.pages.search

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.db.fts.MessageSearchSort
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.ui.hooks.readStringPreference
import me.rerere.rikkahub.ui.hooks.writeStringPreference

private const val TAG = "SearchVM"
private const val SORT_ORDER_PREF_KEY = "search_page_sort_order"

class SearchVM(
    private val context: Application,
    private val conversationRepo: ConversationRepository,
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")

    var searchQuery by mutableStateOf("")
        private set
    var sortOrder by mutableStateOf(
        runCatching {
            MessageSearchSort.valueOf(
                context.readStringPreference(SORT_ORDER_PREF_KEY, MessageSearchSort.RELEVANCE.name)!!
            )
        }.getOrDefault(MessageSearchSort.RELEVANCE)
    )
        private set
    var results by mutableStateOf<List<MessageSearchResult>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRebuilding by mutableStateOf(false)
        private set
    var rebuildProgress by mutableStateOf(0 to 0)
        private set

    /** v217 A3：搜索失败的错误文案；非 null 时页面显示 ErrorState + 重试。 */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300L)
                .collectLatest { query -> performSearch(query) }
        }
    }

    fun onQueryChange(query: String) {
        searchQuery = query
        _searchQuery.value = query
    }

    fun onSortChange(sort: MessageSearchSort) {
        if (sortOrder == sort) return
        sortOrder = sort
        context.writeStringPreference(SORT_ORDER_PREF_KEY, sort.name)
        viewModelScope.launch {
            performSearch(searchQuery)
        }
    }

    fun search() {
        viewModelScope.launch {
            performSearch(searchQuery)
        }
    }

    fun rebuildIndex() {
        viewModelScope.launch {
            isRebuilding = true
            rebuildProgress = 0 to 0
            try {
                conversationRepo.rebuildAllIndexes { current, total ->
                    rebuildProgress = current to total
                }
            } finally {
                isRebuilding = false
            }
        }
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            results = emptyList()
            errorMessage = null
            return
        }
        isLoading = true
        errorMessage = null
        try {
            results = conversationRepo.searchMessages(query, sortOrder)
        } catch (e: Exception) {
            // v217 A3：原实现没有 catch —— 异常会冒泡杀掉 init 里的 collectLatest 协程，
            // 之后输入框再怎么改都不会再触发搜索（静默失效）。现在兜住并暴露成错误态。
            Log.e(TAG, "performSearch failed: ${e.message}", e)
            results = emptyList()
            errorMessage = e.message ?: e.javaClass.simpleName
        } finally {
            isLoading = false
        }
    }
}

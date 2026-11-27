package com.flick.app.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flick.app.data.BlockType
import com.flick.app.data.FolderEntity
import com.flick.app.data.NoteEntity
import com.flick.app.data.NotesRepository
import com.flick.app.data.SettingsStore
import com.flick.app.data.SortMode
import com.flick.app.data.ViewMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Selection {
    data object All : Selection
    data class Folder(val id: String) : Selection
    data object Trash : Selection
}

data class Pill(
    val text: String,
    val actionLabel: String?,
    val action: (() -> Unit)?,
    val id: Long = System.nanoTime(),
)

data class HomeState(
    val notes: List<NoteEntity> = emptyList(),
    val allActive: List<NoteEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val selection: Selection = Selection.All,
    val query: String = "",
    val viewMode: ViewMode = ViewMode.GRID,
    val sort: SortMode = SortMode.RECENT,
    val activeCount: Int = 0,
    val trashCount: Int = 0,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repo: NotesRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    /** What the search field shows right now. */
    val queryUi = MutableStateFlow("")
    private val selection = MutableStateFlow<Selection>(Selection.All)

    val pill = MutableStateFlow<Pill?>(null)

    val hapticsOn: StateFlow<Boolean> =
        settings.haptics.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val debouncedQuery = queryUi.debounce(120)
    private val searchIds = debouncedQuery.flatMapLatest { q ->
        if (q.isBlank()) flowOf(null) else repo.searchIds(q.trim())
    }

    val state: StateFlow<HomeState> = combine(
        combine(repo.observeNotes(), repo.observeTrash(), repo.observeFolders()) { a, t, f -> Triple(a, t, f) },
        combine(selection, debouncedQuery, searchIds) { s, q, ids -> Triple(s, q, ids) },
        combine(settings.viewMode, settings.sortMode) { v, s -> v to s },
    ) { data, filter, prefs ->
        val (active, trashed, folders) = data
        val (sel, q, ids) = filter
        val (view, sort) = prefs

        val sorter = sorter(sort)
        val sortedActive = active.sortedWith(sorter)

        val shown: List<NoteEntity> = when (sel) {
            Selection.Trash -> {
                val base = trashed
                if (q.isBlank()) base else base.filter { it.title.contains(q.trim(), ignoreCase = true) }
            }
            Selection.All -> sortedActive.filterByIds(ids)
            is Selection.Folder -> sortedActive.filter { it.folderId == sel.id }.filterByIds(ids)
        }
        HomeState(
            notes = shown,
            allActive = sortedActive,
            folders = folders,
            selection = sel,
            query = q.trim(),
            viewMode = view,
            sort = sort,
            activeCount = active.size,
            trashCount = trashed.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    init {
        viewModelScope.launch {
            repo.trashEvents.collect { id ->
                pill.value = Pill("Moved to Trash", "Undo", { viewModelScope.launch { repo.restoreNote(id) } })
            }
        }
    }

    private fun List<NoteEntity>.filterByIds(ids: List<String>?): List<NoteEntity> =
        if (ids == null) this else filter { it.id in ids }

    private fun sorter(sort: SortMode): Comparator<NoteEntity> {
        val base: Comparator<NoteEntity> = when (sort) {
            SortMode.RECENT -> compareByDescending { it.updatedAt }
            SortMode.CREATED -> compareByDescending { it.createdAt }
            SortMode.TITLE -> compareBy { it.title.lowercase() }
        }
        return compareByDescending<NoteEntity> { it.pinned }
            .thenByDescending { it.pinnedAt ?: 0L }
            .then(base)
    }

    fun setQuery(q: String) {
        queryUi.value = q
    }

    fun select(s: Selection) {
        selection.value = s
    }

    fun dismissPill(id: Long) {
        if (pill.value?.id == id) pill.value = null
    }

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch {
            if (mode == ViewMode.FLICK && selection.value is Selection.Trash) selection.value = Selection.All
            settings.setViewMode(mode)
        }
    }

    fun setSort(mode: SortMode) = viewModelScope.launch { settings.setSortMode(mode) }
    fun setHaptics(on: Boolean) = viewModelScope.launch { settings.setHaptics(on) }

    // ---- notes ----

    private fun currentFolderId(): String? = (selection.value as? Selection.Folder)?.id

    fun createNote(first: BlockType = BlockType.PARAGRAPH, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(repo.createNote(currentFolderId(), first)) }
    }

    fun createNoteWithImage(uri: Uri, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val path = repo.importImage(uri) ?: return@launch
            onCreated(repo.createNote(currentFolderId(), imagePath = path))
        }
    }

    fun togglePin(note: NoteEntity) = viewModelScope.launch { repo.setPinned(note.id, !note.pinned) }
    fun trash(id: String) = viewModelScope.launch { repo.trashNote(id) }
    fun restore(id: String) = viewModelScope.launch { repo.restoreNote(id) }
    fun deleteForever(id: String) = viewModelScope.launch { repo.deleteForever(id) }
    fun emptyTrash() = viewModelScope.launch { repo.emptyTrash() }
    fun moveNote(id: String, folderId: String?) = viewModelScope.launch { repo.moveNote(id, folderId) }
    fun setLocked(id: String, locked: Boolean) = viewModelScope.launch { repo.setLocked(id, locked) }
    fun toggleBlock(blockId: String) = viewModelScope.launch { repo.toggleBlockChecked(blockId) }

    suspend fun markdown(id: String): String = repo.noteAsMarkdown(id)
    suspend fun exportAll(): String = repo.exportAllMarkdown()

    // ---- folders ----

    fun createFolder(name: String, color: String) = viewModelScope.launch { repo.createFolder(name, color) }
    fun updateFolder(folder: FolderEntity) = viewModelScope.launch { repo.updateFolder(folder) }
    fun moveFolder(id: String, dir: Int) = viewModelScope.launch { repo.moveFolder(id, dir) }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            if ((selection.value as? Selection.Folder)?.id == id) selection.value = Selection.All
            repo.deleteFolder(id)
        }
    }

    fun blocksFor(noteId: String) = repo.observeBlocks(noteId)
}

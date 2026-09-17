package net.openmanet.perfapp.ui.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.openmanet.perfapp.data.export.CsvExporter
import net.openmanet.perfapp.data.export.ShareSheetExporter
import net.openmanet.perfapp.data.export.UploadService
import net.openmanet.perfapp.data.export.UploadSettingsRepository
import java.io.File
import javax.inject.Inject

sealed interface UploadUiState {
    data object Idle : UploadUiState
    data object Uploading : UploadUiState
    data object Success : UploadUiState
    data class Error(val message: String) : UploadUiState
}

/**
 * One combined "Export" action (generate the session's CSV + open the share sheet) instead of
 * separate "Generate" then "Share" taps in sequence - and "Upload" no longer depends on having
 * tapped Export first, since it generates the CSV itself if needed. CsvExporter now always
 * produces at most one file per session (session_log.csv), so there's no longer a list of files
 * to juggle either.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val csvExporter: CsvExporter,
    private val shareSheetExporter: ShareSheetExporter,
    private val uploadService: UploadService,
    private val uploadSettingsRepository: UploadSettingsRepository,
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _file = MutableStateFlow<File?>(null)
    val file: StateFlow<File?> = _file.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uploadState: StateFlow<UploadUiState> = _uploadState.asStateFlow()

    private val _endpointUrl = MutableStateFlow("")
    val endpointUrl: StateFlow<String> = _endpointUrl.asStateFlow()

    init {
        viewModelScope.launch { _endpointUrl.value = uploadSettingsRepository.endpointUrl.first() }
    }

    fun onEndpointUrlChanged(url: String) {
        _endpointUrl.value = url
    }

    /** Generates the session's CSV from the latest data and immediately opens the share sheet. */
    fun exportAndShare() {
        viewModelScope.launch {
            ensureGenerated()?.let { file -> shareSheetExporter.share(listOf(file)) }
        }
    }

    fun upload() {
        val url = _endpointUrl.value
        if (url.isBlank()) return

        viewModelScope.launch {
            val file = ensureGenerated()
            if (file == null) {
                _uploadState.value = UploadUiState.Error("No data recorded for this session yet.")
                return@launch
            }
            _uploadState.value = UploadUiState.Uploading
            uploadSettingsRepository.setEndpointUrl(url)
            val result = uploadService.upload(file, url)
            _uploadState.value = if (result.isSuccess) {
                UploadUiState.Success
            } else {
                UploadUiState.Error(result.exceptionOrNull()?.message ?: "Upload failed")
            }
        }
    }

    private suspend fun ensureGenerated(): File? {
        _isBusy.value = true
        val generated = csvExporter.exportSession(sessionId).firstOrNull()
        _file.value = generated
        _isBusy.value = false
        return generated
    }
}

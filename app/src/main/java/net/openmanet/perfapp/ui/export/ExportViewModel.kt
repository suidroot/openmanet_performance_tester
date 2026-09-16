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

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val csvExporter: CsvExporter,
    private val shareSheetExporter: ShareSheetExporter,
    private val uploadService: UploadService,
    private val uploadSettingsRepository: UploadSettingsRepository,
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

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

    fun generate() {
        viewModelScope.launch {
            _isGenerating.value = true
            _files.value = csvExporter.exportSession(sessionId)
            _isGenerating.value = false
        }
    }

    fun share() {
        shareSheetExporter.share(_files.value)
    }

    fun upload() {
        val url = _endpointUrl.value
        val fileList = _files.value
        if (url.isBlank() || fileList.isEmpty()) return

        viewModelScope.launch {
            _uploadState.value = UploadUiState.Uploading
            uploadSettingsRepository.setEndpointUrl(url)

            for (file in fileList) {
                val target = if (fileList.size > 1) appendSuffix(url, file.nameWithoutExtension) else url
                val result = uploadService.upload(file, target)
                if (result.isFailure) {
                    _uploadState.value = UploadUiState.Error(result.exceptionOrNull()?.message ?: "Upload failed")
                    return@launch
                }
            }
            _uploadState.value = UploadUiState.Success
        }
    }

    /** When exporting multiple files to one presigned-style URL, disambiguate by suffixing the
     * path so each file lands at its own key instead of overwriting the last one uploaded. */
    private fun appendSuffix(url: String, suffix: String): String {
        val queryIndex = url.indexOf('?')
        return if (queryIndex == -1) {
            "$url-$suffix"
        } else {
            url.substring(0, queryIndex) + "-$suffix" + url.substring(queryIndex)
        }
    }
}

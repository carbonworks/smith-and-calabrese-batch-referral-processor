package tech.carbonworks.snc.batchreferralparser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.carbonworks.snc.batchreferralparser.extraction.ExtractionResult
import tech.carbonworks.snc.batchreferralparser.extraction.FieldParser
import tech.carbonworks.snc.batchreferralparser.extraction.PdfTextExtractor
import tech.carbonworks.snc.batchreferralparser.extraction.ReferralFields
import tech.carbonworks.snc.batchreferralparser.extraction.TableExtractor
import tech.carbonworks.snc.batchreferralparser.ui.components.CwCard
import tech.carbonworks.snc.batchreferralparser.ui.components.FilePathText
import tech.carbonworks.snc.batchreferralparser.ui.components.SectionHeader
import tech.carbonworks.snc.batchreferralparser.ui.components.FileStatus
import tech.carbonworks.snc.batchreferralparser.ui.components.StatusIcon
import tech.carbonworks.snc.batchreferralparser.ui.theme.DeepInk
import tech.carbonworks.snc.batchreferralparser.ui.theme.PaperTan
import tech.carbonworks.snc.batchreferralparser.ui.theme.SoftGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.WarmWhite
import java.io.File

/**
 * Result of processing a single referral PDF through the extraction pipeline.
 *
 * @param file the source PDF file
 * @param fields the extracted referral fields (null if extraction failed)
 * @param error error message if extraction failed (null on success)
 */
data class ProcessedReferral(
    val file: File,
    val fields: ReferralFields?,
    val error: String?,
)

/**
 * Per-file tracking state during batch processing.
 */
data class FileProcessingState(
    val file: File,
    val status: FileStatus = FileStatus.PENDING,
    val error: String? = null,
)

/**
 * Processing screen: shows real-time per-file progress during batch extraction.
 *
 * Processes files sequentially in a background coroutine. Each file goes through
 * the full pipeline: PdfTextExtractor -> TableExtractor -> FieldParser. One bad
 * file does not stop the batch.
 *
 * @param files the PDF files to process
 * @param fileStates per-file processing state for UI display
 * @param onFileStateUpdate callback to update a single file's state
 * @param onComplete callback when all files are processed, with results
 */
@Composable
fun ProcessingScreen(
    files: List<File>,
    fileStates: List<FileProcessingState>,
    onFileStateUpdate: (index: Int, FileProcessingState) -> Unit,
    onComplete: (List<ProcessedReferral>) -> Unit,
) {
    val completedCount = fileStates.count {
        it.status == FileStatus.SUCCESS || it.status == FileStatus.ERROR
    }
    val progress = if (files.isEmpty()) 1f else completedCount.toFloat() / files.size

    // Launch batch processing
    LaunchedEffect(files) {
        val results = mutableListOf<ProcessedReferral>()
        val textExtractor = PdfTextExtractor()
        val tableExtractor = TableExtractor()
        val fieldParser = FieldParser()

        for ((index, file) in files.withIndex()) {
            onFileStateUpdate(index, FileProcessingState(file, FileStatus.PROCESSING))

            val result = withContext(Dispatchers.IO) {
                try {
                    val textResult = textExtractor.extract(file)

                    if (textResult is ExtractionResult.Error) {
                        ProcessedReferral(file, null, textResult.message)
                    } else {
                        val success = textResult as ExtractionResult.Success
                        val tables = tableExtractor.extract(file)
                        val fields = fieldParser.parse(success, tables)
                        ProcessedReferral(file, fields, null)
                    }
                } catch (e: Exception) {
                    ProcessedReferral(
                        file,
                        null,
                        "Unexpected error: ${e.message ?: "Unknown error"}",
                    )
                }
            }

            results.add(result)

            val status = if (result.error != null) FileStatus.ERROR else FileStatus.SUCCESS
            onFileStateUpdate(index, FileProcessingState(file, status, result.error))
        }

        onComplete(results)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmWhite)
            .padding(32.dp),
    ) {
        // Header
        Text(
            text = "Processing Referrals",
            style = MaterialTheme.typography.headlineSmall,
            color = DeepInk,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Extracting data from ${files.size} PDF file${if (files.size != 1) "s" else ""}...",
            style = MaterialTheme.typography.bodyMedium,
            color = SoftGray,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Progress bar
        CwCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Progress",
                        style = MaterialTheme.typography.titleSmall,
                        color = DeepInk,
                    )
                    Text(
                        text = "$completedCount / ${files.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftGray,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = PaperTan,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Per-file status list
        SectionHeader(text = "File Status")

        CwCard(
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier.padding(8.dp),
            ) {
                items(fileStates) { state ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusIcon(status = state.status)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            FilePathText(path = state.file.name)
                            if (state.error != null) {
                                Text(
                                    text = state.error,
                                    fontSize = 12.sp,
                                    color = SoftGray,
                                    maxLines = 2,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (state.status) {
                                FileStatus.PENDING -> "Waiting"
                                FileStatus.PROCESSING -> "Extracting..."
                                FileStatus.SUCCESS -> "Done"
                                FileStatus.ERROR -> "Failed"
                            },
                            fontSize = 12.sp,
                            color = when (state.status) {
                                FileStatus.PROCESSING -> PaperTan
                                FileStatus.ERROR -> SoftGray
                                else -> SoftGray
                            },
                        )
                    }
                }
            }
        }
    }
}

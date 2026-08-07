package dev.hyperears.diagnostics

import android.content.Context
import android.net.Uri
import android.os.Build
import dev.hyperears.BuildConfig
import dev.hyperears.root.RootShell
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class DiagnosticExportResult(
    val success: Boolean,
    val detail: String,
)

internal data class DiagnosticReport(
    val generatedAt: String,
    val version: String,
    val device: String,
    val androidVersion: String,
    val diagnosticLoggingEnabled: Boolean,
    val lsposedLog: String,
    val lsposedError: String?,
    val appLog: String,
) {
    fun render(): String = buildString {
        appendLine("HyperEars diagnostics")
        appendLine("Generated: $generatedAt")
        appendLine("Version: $version")
        appendLine("Device: $device")
        appendLine("Android: $androidVersion")
        appendLine("Detailed logging: ${if (diagnosticLoggingEnabled) "enabled" else "disabled"}")
        appendLine()
        appendLine("===== LSPosed module log =====")
        if (lsposedLog.isNotBlank()) {
            appendLine(lsposedLog.trim())
        } else {
            appendLine("No HyperEars entries were found.")
            appendLine("In LSPosed, disable \"禁用详细日志\" and enable \"输出日志到守护进程\".")
        }
        if (!lsposedError.isNullOrBlank()) {
            appendLine("LSPosed export error: ${lsposedError.trim()}")
        }
        appendLine()
        appendLine("===== HyperEars app log =====")
        appendLine(appLog.ifBlank { "No companion-app entries were recorded." }.trim())
    }
}

/** Exports filtered LSPosed daemon logs together with the companion application's bounded log. */
internal object DiagnosticLogExporter {
    fun defaultFileName(): String =
        "HyperEars-logs-${LocalDateTime.now().format(FILE_NAME_TIME_FORMAT)}.txt"

    suspend fun export(
        context: Context,
        destination: Uri,
        diagnosticLoggingEnabled: Boolean,
    ): DiagnosticExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val lsposedResult = RootShell.execute(
                command = LSPOSED_LOG_COMMAND,
                timeoutSeconds = EXPORT_TIMEOUT_SECONDS,
            )
            val report = DiagnosticReport(
                generatedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidVersion = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                diagnosticLoggingEnabled = diagnosticLoggingEnabled,
                lsposedLog = if (lsposedResult.success) {
                    lsposedResult.output.limitToNewestEntries()
                } else {
                    ""
                },
                lsposedError = if (lsposedResult.success) null else lsposedResult.describe("读取"),
                appLog = AppDiagnosticLog.read(context),
            )
            val output = requireNotNull(context.contentResolver.openOutputStream(destination, "w")) {
                "无法打开导出位置"
            }
            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(report.render())
            }
            DiagnosticExportResult(true, "日志已导出")
        }.getOrElse { error ->
            DiagnosticExportResult(false, error.message ?: error.javaClass.simpleName)
        }
    }

    private val FILE_NAME_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    private const val EXPORT_TIMEOUT_SECONDS = 20L
    private const val MAX_LSPOSED_LOG_CHARS = 2 * 1024 * 1024

    private val LSPOSED_LOG_COMMAND = """
        for d in /data/adb/lspd/log.old /data/adb/lspd/log
        do
          [ -d "${'$'}d" ] || continue
          for f in "${'$'}d"/modules_*.log "${'$'}d"/modules.log
          do
            [ -f "${'$'}f" ] || continue
            awk -v file="${'$'}f" '
              /^\[ / {
                keep = index(${'$'}0, "[dev.hyperears,") > 0
                if (keep && !header) {
                  printf "\\n===== %s =====\\n", file
                  header = 1
                }
              }
              keep { print }
            ' "${'$'}f"
          done
        done
    """.trimIndent()

    private fun String.limitToNewestEntries(): String =
        if (length <= MAX_LSPOSED_LOG_CHARS) {
            this
        } else {
            "[Earlier LSPosed entries omitted]\n" + takeLast(MAX_LSPOSED_LOG_CHARS)
        }
}

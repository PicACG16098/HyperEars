package dev.hyperears.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportTest {
    @Test
    fun renderKeepsBothLogSourcesSeparated() {
        val rendered = report(
            lsposedLog = "injected-process-entry",
            appLog = "quick-action-entry",
        ).render()

        assertTrue(rendered.contains("===== LSPosed module log ====="))
        assertTrue(rendered.contains("injected-process-entry"))
        assertTrue(rendered.contains("===== HyperEars app log ====="))
        assertTrue(rendered.contains("quick-action-entry"))
        assertFalse(rendered.contains("No HyperEars entries were found."))
    }

    @Test
    fun renderExplainsRequiredLsposedSettingsWhenNoEntriesExist() {
        val rendered = report(lsposedLog = "", appLog = "").render()

        assertTrue(rendered.contains("No HyperEars entries were found."))
        assertTrue(rendered.contains("禁用详细日志"))
        assertTrue(rendered.contains("输出日志到守护进程"))
    }

    private fun report(
        lsposedLog: String,
        appLog: String,
    ) = DiagnosticReport(
        generatedAt = "2026-08-07T16:00:00+08:00",
        version = "1.3.0 (130)",
        device = "Xiaomi test",
        androidVersion = "16 (SDK 36)",
        diagnosticLoggingEnabled = true,
        lsposedLog = lsposedLog,
        lsposedError = null,
        appLog = appLog,
    )
}

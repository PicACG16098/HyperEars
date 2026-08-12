package dev.hyperears.update

import android.content.Context
import androidx.core.content.edit
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReleaseInfo(
    val version: String,
    val pageUrl: String,
)

sealed interface UpdateCheckResult {
    data class Available(val release: ReleaseInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

data class UpdateCheckUiState(
    val checking: Boolean = false,
    val result: UpdateCheckResult? = null,
    val showAvailableDialog: Boolean = false,
)

/**
 * App-process update coordinator.
 *
 * Automatic checks run only while HyperEars is opened and are limited to one attempt per day.
 * No worker, alarm, foreground service or injected process performs network access.
 */
class UpdateCheckCoordinator(
    private val preferences: UpdateCheckPreferences,
    private val scope: CoroutineScope,
    private val currentVersion: String,
    private val checker: GitHubReleaseChecker = GitHubReleaseChecker(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(UpdateCheckUiState())
    private var activeCheck: Job? = null

    val state: StateFlow<UpdateCheckUiState> = mutableState.asStateFlow()

    fun checkAutomatically() {
        val currentTime = now()
        val lastAttempt = preferences.lastAutomaticCheckAt
        if (
            lastAttempt in 1..currentTime &&
            currentTime - lastAttempt < AUTO_CHECK_INTERVAL_MILLIS
        ) {
            return
        }
        preferences.lastAutomaticCheckAt = currentTime
        startCheck()
    }

    fun checkManually() {
        startCheck()
    }

    fun dismissAvailableDialog() {
        mutableState.value = mutableState.value.copy(showAvailableDialog = false)
    }

    private fun startCheck() {
        if (activeCheck?.isActive == true) return
        mutableState.value = mutableState.value.copy(checking = true)
        activeCheck = scope.launch {
            val result = checker.check(currentVersion)
            mutableState.value = UpdateCheckUiState(
                checking = false,
                result = result,
                showAvailableDialog = result is UpdateCheckResult.Available,
            )
        }
    }

    private companion object {
        const val AUTO_CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

/** App-local update policy; it is deliberately not mirrored into injected processes. */
class UpdateCheckPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        UPDATE_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    var automaticChecksEnabled: Boolean
        get() = preferences.getBoolean(AUTOMATIC_CHECKS_ENABLED, true)
        set(value) {
            preferences.edit { putBoolean(AUTOMATIC_CHECKS_ENABLED, value) }
        }

    internal var lastAutomaticCheckAt: Long
        get() = preferences.getLong(LAST_AUTO_CHECK_AT, 0L)
        set(value) {
            preferences.edit { putLong(LAST_AUTO_CHECK_AT, value) }
        }

    private companion object {
        const val UPDATE_PREFERENCES = "update_check"
        const val AUTOMATIC_CHECKS_ENABLED = "automatic_checks_enabled"
        const val LAST_AUTO_CHECK_AT = "last_auto_check_at"
    }
}

class GitHubReleaseChecker(
    private val endpoint: URL = URL(LATEST_RELEASE_URL),
) {
    suspend fun check(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetchLatestRelease()
            if (VersionOrder.compare(release.version, currentVersion) > 0) {
                UpdateCheckResult.Available(release)
            } else {
                UpdateCheckResult.UpToDate
            }
        }.getOrElse {
            UpdateCheckResult.Failed("检查失败，请稍后重试")
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo {
        val connection = endpoint.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "text/html")
            connection.setRequestProperty("User-Agent", "HyperEars-Update-Checker")
            val status = connection.responseCode
            if (status !in REDIRECT_STATUS_CODES) {
                error("GitHub 返回 HTTP $status")
            }
            val location = connection.getHeaderField("Location") ?: error("Release 地址缺失")
            val resolved = endpoint.toURI().resolve(location).toString()
            GitHubReleaseUrl.parse(resolved) ?: error("Release 地址无效")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://github.com/silverpoetry/HyperEars/releases/latest"
        const val NETWORK_TIMEOUT_MILLIS = 10_000
        val REDIRECT_STATUS_CODES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER,
            307,
            308,
        )
    }
}

internal object GitHubReleaseUrl {
    private const val RELEASE_PATH_PREFIX = "/silverpoetry/HyperEars/releases/tag/"

    fun parse(value: String): ReleaseInfo? = runCatching {
        val uri = URI(value)
        if (uri.scheme != "https" || !uri.host.equals("github.com", ignoreCase = true)) {
            return null
        }
        val version = uri.path
            .takeIf { it.startsWith(RELEASE_PATH_PREFIX) }
            ?.removePrefix(RELEASE_PATH_PREFIX)
            ?.takeIf { '/' !in it }
            ?.removePrefix("v")
            ?: return null
        if (!VersionOrder.isValid(version)) return null
        ReleaseInfo(version = version, pageUrl = uri.toString())
    }.getOrNull()
}

/** Small SemVer-compatible comparator for release tags; build metadata is intentionally ignored. */
object VersionOrder {
    fun isValid(value: String): Boolean = parse(value) != null

    fun compare(first: String, second: String): Int {
        val left = requireNotNull(parse(first)) { "Invalid version: $first" }
        val right = requireNotNull(parse(second)) { "Invalid version: $second" }
        val width = maxOf(left.numbers.size, right.numbers.size)
        repeat(width) { index ->
            val result = (left.numbers.getOrNull(index) ?: 0)
                .compareTo(right.numbers.getOrNull(index) ?: 0)
            if (result != 0) return result
        }
        val leftPre = left.preRelease
        val rightPre = right.preRelease
        if (leftPre == null && rightPre != null) return 1
        if (leftPre != null && rightPre == null) return -1
        if (leftPre == null) return 0
        val count = maxOf(leftPre.size, requireNotNull(rightPre).size)
        repeat(count) { index ->
            val leftPart = leftPre.getOrNull(index) ?: return -1
            val rightPart = rightPre.getOrNull(index) ?: return 1
            val result = comparePreReleasePart(leftPart, rightPart)
            if (result != 0) return result
        }
        return 0
    }

    private fun comparePreReleasePart(left: String, right: String): Int {
        val leftNumber = left.toLongOrNull()
        val rightNumber = right.toLongOrNull()
        return when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> left.compareTo(right, ignoreCase = true)
        }
    }

    private fun parse(value: String): ParsedVersion? {
        val normalized = value.trim().removePrefix("v").substringBefore('+')
        val main = normalized.substringBefore('-')
        val numbers = main.split('.').map { it.toIntOrNull() ?: return null }
        if (numbers.isEmpty()) return null
        val preRelease = normalized.substringAfter('-', missingDelimiterValue = "")
            .takeIf(String::isNotEmpty)
            ?.split('.')
        if (preRelease?.any(String::isEmpty) == true) return null
        return ParsedVersion(numbers, preRelease)
    }

    private data class ParsedVersion(
        val numbers: List<Int>,
        val preRelease: List<String>?,
    )
}

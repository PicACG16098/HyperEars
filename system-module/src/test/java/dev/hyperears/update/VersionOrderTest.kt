package dev.hyperears.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionOrderTest {
    @Test
    fun comparesReleaseVersionsNumerically() {
        assertTrue(VersionOrder.compare("2.10.0", "2.9.9") > 0)
        assertEquals(0, VersionOrder.compare("v2.2.1", "2.2.1+build.7"))
        assertTrue(VersionOrder.compare("2.2.1", "2.2.1-rc.1") > 0)
        assertTrue(VersionOrder.compare("2.2.1-rc.2", "2.2.1-rc.1") > 0)
    }

    @Test
    fun rejectsNonNumericReleaseTags() {
        assertFalse(VersionOrder.isValid("latest"))
        assertFalse(VersionOrder.isValid("2.x.1"))
        assertTrue(VersionOrder.isValid("v2.2.1"))
    }

    @Test
    fun parsesOnlyTheRepositoriesTrustedReleaseRedirect() {
        assertEquals(
            ReleaseInfo(
                version = "2.2.1",
                pageUrl = "https://github.com/silverpoetry/HyperEars/releases/tag/v2.2.1",
            ),
            GitHubReleaseUrl.parse(
                "https://github.com/silverpoetry/HyperEars/releases/tag/v2.2.1",
            ),
        )
        assertEquals(
            null,
            GitHubReleaseUrl.parse(
                "https://github.com/other/HyperEars/releases/tag/v99.0.0",
            ),
        )
        assertEquals(
            null,
            GitHubReleaseUrl.parse(
                "https://example.com/silverpoetry/HyperEars/releases/tag/v99.0.0",
            ),
        )
    }
}

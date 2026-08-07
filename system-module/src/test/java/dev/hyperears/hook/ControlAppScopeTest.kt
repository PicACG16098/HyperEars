package dev.hyperears.hook

import dev.hyperears.integration.ControlAppCatalog
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlAppScopeTest {
    @Test
    fun everyControllerPackageIsDeclaredAsAnXposedScope() {
        val stream = javaClass.classLoader
            ?.getResourceAsStream("META-INF/xposed/scope.list")
        assertNotNull(stream)
        val scopes = requireNotNull(stream)
            .bufferedReader()
            .useLines { lines ->
                lines.map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSet()
            }

        assertTrue(scopes.containsAll(ControlAppCatalog.packageNames))
    }
}

package dev.hyperears.hook

import dev.hyperears.hook.MiLinkDeviceOwnershipRegistry.Owner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiLinkDeviceOwnershipRegistryTest {
    @Test
    fun positiveNativeAdmissionAlwaysClaimsSystemOwnership() {
        val registry = MiLinkDeviceOwnershipRegistry()

        val first = registry.observeNativeAdmission(ADDRESS, 1, true)
        val repeated = registry.observeNativeAdmission(ADDRESS.lowercase(), true, true)

        assertEquals(Owner.SYSTEM, first.owner)
        assertTrue(first.systemOwnershipNewlyClaimed)
        assertFalse(first.hyperEarsOwnershipNewlyClaimed)
        assertEquals(Owner.SYSTEM, repeated.owner)
        assertFalse(repeated.systemOwnershipNewlyClaimed)
        assertFalse(registry.isHyperEarsOwned(ADDRESS))
    }

    @Test
    fun nativeRejectionAllowsAnActiveHyperEarsCandidateToSupplementMiLink() {
        val registry = MiLinkDeviceOwnershipRegistry()

        val decision = registry.observeNativeAdmission(ADDRESS, 0, true)

        assertEquals(Owner.HYPEREARS, decision.owner)
        assertTrue(registry.isHyperEarsOwned(ADDRESS))
        assertFalse(decision.systemOwnershipNewlyClaimed)
        assertTrue(decision.hyperEarsOwnershipNewlyClaimed)
    }

    @Test
    fun systemOwnershipCannotBeDowngradedByALaterNativeRejection() {
        val registry = MiLinkDeviceOwnershipRegistry()
        registry.observeNativeAdmission(ADDRESS, true, false)

        val decision = registry.observeNativeAdmission(ADDRESS, false, true)

        assertEquals(Owner.SYSTEM, decision.owner)
        assertFalse(decision.systemOwnershipNewlyClaimed)
    }

    @Test
    fun laterAuthoritativeNativeAcceptancePromotesAProvisionalHyperEarsOwner() {
        val registry = MiLinkDeviceOwnershipRegistry()
        registry.observeNativeAdmission(ADDRESS, 0, true)

        val accepted = registry.observeNativeAdmission(ADDRESS, 1, true)

        assertEquals(Owner.SYSTEM, accepted.owner)
        assertTrue(accepted.systemOwnershipNewlyClaimed)
        assertFalse(accepted.hyperEarsOwnershipNewlyClaimed)
    }

    @Test
    fun sharedSystemClaimOverridesAProcessLocalCandidate() {
        val registry = MiLinkDeviceOwnershipRegistry()
        registry.observeNativeAdmission(ADDRESS, 0, true)

        assertTrue(registry.claimSystemOwnership(ADDRESS.lowercase()))
        assertEquals(Owner.SYSTEM, registry.owner(ADDRESS))
        assertFalse(registry.claimSystemOwnership(ADDRESS))
    }

    @Test
    fun unsupportedReturnShapesAreNotTreatedAsNativeAcceptance() {
        assertTrue(MiLinkDeviceOwnershipRegistry.isNativeAdmissionAccepted(1))
        assertTrue(MiLinkDeviceOwnershipRegistry.isNativeAdmissionAccepted(true))
        assertFalse(MiLinkDeviceOwnershipRegistry.isNativeAdmissionAccepted(0))
        assertFalse(MiLinkDeviceOwnershipRegistry.isNativeAdmissionAccepted(-1))
        assertFalse(MiLinkDeviceOwnershipRegistry.isNativeAdmissionAccepted(false))
        assertFalse(MiLinkDeviceOwnershipRegistry.isNativeAdmissionAccepted(null))
        assertFalse(MiLinkDeviceOwnershipRegistry.isNativeAdmissionAccepted("1"))
    }

    private companion object {
        const val ADDRESS = "AA:BB:CC:DD:EE:FF"
    }
}

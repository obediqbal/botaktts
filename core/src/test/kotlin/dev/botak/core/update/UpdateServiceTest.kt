package dev.botak.core.update

import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Unit tests for [UpdateService]. */
class UpdateServiceTest {
    private fun service(
        current: SemVer,
        release: () -> ReleaseInfo,
    ): UpdateService {
        val versionProvider = mockk<VersionProvider>()
        every { versionProvider.currentVersion() } returns current
        val releaseClient = mockk<GitHubReleaseClient>()
        every { releaseClient.fetchLatest() } answers { release() }
        return UpdateService(versionProvider, releaseClient)
    }

    @Test
    fun `returns UpdateAvailable when latest is newer`() {
        val svc =
            service(SemVer(1, 0, 0)) {
                ReleaseInfo(SemVer(1, 1, 0), "notes", "https://example/setup", "https://example/page")
            }
        val result = svc.checkForUpdate()
        val available = assertIs<UpdateCheckResult.UpdateAvailable>(result)
        assertEquals(SemVer(1, 1, 0), available.latest)
        assertEquals("https://example/setup", available.setupAssetUrl)
    }

    @Test
    fun `returns UpToDate when latest equals current`() {
        val svc =
            service(SemVer(1, 1, 0)) {
                ReleaseInfo(SemVer(1, 1, 0), "", null, "x")
            }
        assertIs<UpdateCheckResult.UpToDate>(svc.checkForUpdate())
    }

    @Test
    fun `returns UpToDate when latest is older`() {
        val svc =
            service(SemVer(2, 0, 0)) {
                ReleaseInfo(SemVer(1, 9, 9), "", null, "x")
            }
        assertIs<UpdateCheckResult.UpToDate>(svc.checkForUpdate())
    }

    @Test
    fun `returns Failed when client throws and never propagates`() {
        val svc = service(SemVer(1, 0, 0)) { throw RuntimeException("offline") }
        val result = svc.checkForUpdate()
        val failed = assertIs<UpdateCheckResult.Failed>(result)
        assertEquals("offline", failed.reason)
    }
}

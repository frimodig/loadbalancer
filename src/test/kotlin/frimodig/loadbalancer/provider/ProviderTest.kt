package frimodig.loadbalancer.provider

import io.mockk.every
import io.mockk.spyk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ProviderTest {

    @Nested
    inner class Heartbeat {
        private val provider = spyk(Provider("provider"))

        @Test
        fun `Failing provider will be marked as unhealthy`() {
            every { provider.check() } throws Exception()

            provider.heartbeat()

            assert(!provider.status.healthy)
            assert(!provider.status.previousHeartbeatOK)
        }

        @Test
        fun `Unhealthy provider will be marked as healthy after two successful heartbeats`() {
            provider.status = ProviderStatus(healthy = false, previousHeartbeatOK = false)

            provider.heartbeat()
            provider.heartbeat()

            assert(provider.status.healthy)
            assert(provider.status.previousHeartbeatOK)
        }
    }
}

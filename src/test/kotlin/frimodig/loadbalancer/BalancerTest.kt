package frimodig.loadbalancer

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BalancerTest {

    @Nested
    inner class RegisterProviders {

        private lateinit var balancer: Balancer

        @BeforeEach
        fun setup() {
            balancer = Balancer()
        }

        @Test
        fun `Will ignore duplicate providers`() {
            balancer.registerProvider("a", "a")

            assert(balancer.providerIds().size == 1)
        }

        @Test
        fun `Will throw an exception if registering would would go over provider limit`() {
            val providers = (1..11).toList().map { it.toString() }
            assertThrows<IllegalStateException> {
                balancer.registerProvider(*providers.toTypedArray())
            }
        }

        @Test
        fun `Will remove a provider`() {
            val id = "id"
            balancer.registerProvider(id)
            balancer.removeProvider(id)

            assert(balancer.providerIds().isEmpty())
        }
    }
}
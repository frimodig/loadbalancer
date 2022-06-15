package frimodig.loadbalancer

import main.kotlin.frimodig.loadbalancer.Algorithm
import main.kotlin.frimodig.loadbalancer.provider.Provider
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AlgorithmTest {

    private val providers = listOf(
        Provider("1"),
        Provider("2"),
        Provider("3"),
    )

    @Nested
    inner class Random {

        @Test
        fun `should return one from the list of provider`() {
            val provider = Algorithm.RANDOM.get(providers)

            assert(providers.contains(provider))
        }
    }

    @Nested
    inner class RoundRobin {

        @Test
        fun `should return providers in order on consecutive calls`() {
            val result = mutableListOf<Provider>()
            repeat(providers.size) {
                result.add(Algorithm.ROUND_ROBIN.get(providers))
            }

            assert(providers == result)
        }

        @Test
        fun `should loop the list of providers`() {
            val expected = listOf(
                providers.last(),
                providers.first()
            )

            val result = mutableListOf<Provider>()
            repeat(providers.size - 1) {
                Algorithm.ROUND_ROBIN.get(providers)
            }
            repeat(2) {
                result.add(Algorithm.ROUND_ROBIN.get(providers))
            }

            assert(expected == result)
        }
    }
}
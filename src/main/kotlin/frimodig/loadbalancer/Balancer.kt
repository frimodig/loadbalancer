package frimodig.loadbalancer

import frimodig.loadbalancer.provider.Provider
import mu.KotlinLogging
import java.util.Timer
import java.util.TimerTask

const val providerLimit = 10
const val heartbeatIntervalMs = 10_000L

private val logger = KotlinLogging.logger {}

class Balancer {

    private val timer = Timer()
    init {
        timer.schedule(object : TimerTask() {
            override fun run() {
                providers.forEach {
                    try {
                        it.value.check()
                    } catch (e: Exception) {
                        logger.warn(e) { "Provider ${it.key} removed from pool as unhealthy" }
                        providers.remove(it.key)
                    }
                }
            }
        }, heartbeatIntervalMs, heartbeatIntervalMs)
    }

    private var algorithm = Algorithm.RANDOM
    private val providers = mutableMapOf<String, Provider>()

    fun get(): String = algorithm.get(providers.values.toList()).get()

    fun setAlgorithm(algorithm: Algorithm) {
        this.algorithm = algorithm
        logger.info { "Balancing algorithm set to ${algorithm.name}" }
    }

    fun registerProvider(vararg identifier: String) {
        // For now at least, we will ignore duplicate providers
        val newProviderIds = identifier.filter { !providers.containsKey(it) }
        check(providers.size + newProviderIds.size < providerLimit) {
            "Can not register ${newProviderIds.size} new providers. Current providers: ${providers.size}, limit: $providerLimit"
        }
        newProviderIds.forEach {
            providers[it] = Provider(it)
            logger.info { "Provider $it added to the pool" }
        }
    }

    fun removeProvider(vararg identifier: String) {
        identifier.forEach {
            providers.remove(it)
            logger.info { "Provider $it removed from pool" }
        }
    }

    fun providers() = providers.keys
}

enum class Algorithm: Selector {
    RANDOM {
        override fun get(providers: List<Provider>) = providers.random()
    },
    ROUND_ROBIN {
        private var previous = -1
        override fun get(providers: List<Provider>): Provider {
            val current = ++previous
            if (previous == providers.lastIndex) {
                previous = -1
            }
            return providers[current]
        }
    }
}

interface Selector {
    fun get(providers: List<Provider>): Provider
}
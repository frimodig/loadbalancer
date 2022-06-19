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
    private var algorithm = Algorithm.RANDOM
    private val providers = mutableListOf<Provider>()
    private val unhealthyProviders = mutableMapOf<Provider, Boolean>()

    init {
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    providers.forEach {
                        try {
                            it.check()
                        } catch (e: Exception) {
                            providers.remove(it)
                            logger.error(e) { "Provider ${it.identifier} removed from pool as unhealthy" }
                            unhealthyProviders[it] = false
                        }
                    }
                    unhealthyProviders.forEach {
                        val (provider, previousCheckSuccessful) = it
                        try {
                            provider.check()
                            if (previousCheckSuccessful) {
                                unhealthyProviders.remove(provider)
                                providers.add(provider)
                                logger.info { "Provider ${provider.identifier} returned to the pool" }
                            } else {
                                unhealthyProviders[provider] = false
                            }
                        } catch (e: Exception) {
                            logger.warn(e) { "Provider ${provider.identifier} is still unhealthy" }
                            unhealthyProviders[provider] = false
                        }
                    }
                }
            },
            heartbeatIntervalMs,
            heartbeatIntervalMs
        )
    }

    fun get(): String = algorithm.get(providers).get()

    fun setAlgorithm(algorithm: Algorithm) {
        this.algorithm = algorithm
        logger.info { "Balancing algorithm set to ${algorithm.name}" }
    }

    fun registerProvider(vararg identifier: String) {
        // For now at least, we will ignore duplicate providers
        val newProviderIds = identifier.distinct().filter { !providerIds().contains(it) && !unhealthyProviderIds().contains(it) }
        check(providers.size + newProviderIds.size < providerLimit) {
            "Can not register ${newProviderIds.size} new providers. Current providers: ${providers.size}, limit: $providerLimit"
        }
        newProviderIds.forEach {
            providers.add(Provider(it))
            logger.info { "Provider $it added to the pool" }
        }
    }

    fun removeProvider(vararg identifier: String) {
        identifier.distinct().forEach {
            providers.removeIf { provider -> provider.identifier == it }
            unhealthyProviders.remove(unhealthyProviders.keys.firstOrNull { provider -> provider.identifier == it })
            logger.info { "Provider $it removed from pool" }
        }
    }

    fun providerIds() = providers.map { it.identifier }
    fun unhealthyProviderIds() = unhealthyProviders.map { it.key.identifier }
}

enum class Algorithm : Selector {
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

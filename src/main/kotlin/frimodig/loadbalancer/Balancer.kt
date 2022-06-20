package frimodig.loadbalancer

import frimodig.loadbalancer.provider.Provider
import frimodig.loadbalancer.ratelimiter.RateLimiter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.util.Timer
import java.util.TimerTask

const val providerLimit = 10
const val providerMaxRequests = 10
const val heartbeatIntervalMs = 10_000L

private val logger = KotlinLogging.logger {}

class Balancer {

    private val timer = Timer()
    private val rateLimiter = RateLimiter()
    private var algorithm = Algorithm.RANDOM
    private val providers = mutableListOf<Provider>()

    init {
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    providers.forEach {
                        it.heartbeat()
                    }
                    rateLimiter.setMaxRequests(activeProviders().size)
                }
            },
            heartbeatIntervalMs,
            heartbeatIntervalMs
        )
    }

    suspend fun get(): String = withContext(Dispatchers.Default) {
        rateLimiter.semaphore().withPermit {
            algorithm.get(activeProviders()).get()
        }
    }

    fun setAlgorithm(algorithm: Algorithm) {
        this.algorithm = algorithm
        logger.info { "Balancing algorithm set to ${algorithm.name}" }
    }

    fun registerProvider(vararg identifier: String) {
        val newProviderIds = identifier.distinct().filter { !providerIds().contains(it) }
        check(providers.size + newProviderIds.size < providerLimit) {
            "Can not register ${newProviderIds.size} new providers. Current providers: ${providers.size}, limit: $providerLimit"
        }
        newProviderIds.forEach {
            providers.add(Provider(it))
            logger.info { "Provider $it added to the pool" }
        }
        rateLimiter.setMaxRequests(activeProviders().size)
    }

    fun removeProvider(vararg identifier: String) {
        identifier.distinct().forEach {
            providers.removeIf { provider -> provider.identifier == it }
            logger.info { "Provider $it removed from pool" }
        }
        rateLimiter.setMaxRequests(activeProviders().size)
    }

    fun providerIds() = providers.map { it.identifier }
    fun activeProviders() = providers.filter { it.status.healthy }
}

enum class Algorithm : Selector {
    RANDOM {
        override fun get(providers: List<Provider>) = providers.random()
    },
    ROUND_ROBIN {
        private var previous = -1
        override fun get(providers: List<Provider>): Provider {
            val current = ++previous
            if (previous >= providers.lastIndex) {
                previous = -1
            }
            return providers[current]
        }
    }
}

interface Selector {
    fun get(providers: List<Provider>): Provider
}

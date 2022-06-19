package frimodig.loadbalancer

import frimodig.loadbalancer.provider.Provider
import frimodig.loadbalancer.provider.ProviderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

const val providerLimit = 10
const val providerMaxRequests = 10
const val heartbeatIntervalMs = 10_000L

private val logger = KotlinLogging.logger {}

class Balancer {

    private val semaphoreLock = ReentrantLock()
    private var semaphore = Semaphore(1)
    private var currentMaxRequests = 1

    private val timer = Timer()
    private var algorithm = Algorithm.RANDOM
    private val providers = mutableListOf<Provider>()

    init {
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    providers.forEach {
                        heartbeat(it)
                    }
                    setMaxRequests()
                }
            },
            heartbeatIntervalMs,
            heartbeatIntervalMs
        )
    }

    suspend fun get(): String = withContext(Dispatchers.Default) {
        semaphore.withPermit {
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
        setMaxRequests()
    }

    fun removeProvider(vararg identifier: String) {
        identifier.distinct().forEach {
            providers.removeIf { provider -> provider.identifier == it }
            logger.info { "Provider $it removed from pool" }
        }
        setMaxRequests()
    }

    fun providerIds() = providers.map { it.identifier }
    fun activeProviders() = providers.filter { it.status.healthy }

    private fun heartbeat(provider: Provider) {
        if (provider.status.healthy) try {
            provider.check()
        } catch (e: Exception) {
            provider.status = ProviderStatus(healthy = false, previousHeartbeatOK = false)
            logger.error(e) { "Provider ${provider.identifier} removed from pool as unhealthy" }
        }
        else {
            try {
                provider.check()
                if (provider.status.previousHeartbeatOK) {
                    provider.status = ProviderStatus(healthy = true, previousHeartbeatOK = true)
                } else {
                    provider.status = ProviderStatus(healthy = false, previousHeartbeatOK = true)
                }
            } catch (e: Exception) {
                provider.status = ProviderStatus(healthy = false, previousHeartbeatOK = false)
                logger.warn(e) { "Provider ${provider.identifier} is still unhealthy" }
            }
        }
    }

    private fun setMaxRequests() {
        semaphoreLock.withLock {
            // Semaphore needs at least 1 permit and inUse can't
            val inUse = currentMaxRequests - semaphore.availablePermits
            val newMax = maxOf(activeProviders().size * providerMaxRequests, 1)
            semaphore = Semaphore(
                permits = newMax,
                acquiredPermits = inUse
            )
            currentMaxRequests = newMax
            logger.info { "Max concurrent requests set to $newMax" }
        }
    }
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

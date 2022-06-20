package frimodig.loadbalancer.ratelimiter

import frimodig.loadbalancer.providerMaxRequests
import kotlinx.coroutines.sync.Semaphore
import mu.KotlinLogging
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

class RateLimiter {

    private val semaphoreLock = ReentrantLock()
    private var semaphore = Semaphore(1)
    private var currentMaxRequests = 1

    fun semaphore() = semaphore

    fun setMaxRequests(activeProviders: Int) {
        semaphoreLock.withLock {
            // Semaphore needs at least 1 permit and inUse can't
            val inUse = currentMaxRequests - semaphore.availablePermits
            val newMax = maxOf(activeProviders * providerMaxRequests, 1)
            semaphore = Semaphore(
                permits = newMax,
                acquiredPermits = inUse
            )
            currentMaxRequests = newMax
            logger.info { "Set max concurrent requests to $newMax" }
        }
    }
}

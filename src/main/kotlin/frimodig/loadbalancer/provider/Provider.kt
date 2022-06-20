package frimodig.loadbalancer.provider

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

data class Provider(
    val identifier: String,
    var status: ProviderStatus = ProviderStatus(),
) {
    fun get() = identifier

    fun check() {}

    fun heartbeat() {
        if (status.healthy) try {
            check()
        } catch (e: Exception) {
            status = ProviderStatus(healthy = false, previousHeartbeatOK = false)
            logger.error(e) { "Provider $identifier removed from pool as unhealthy" }
        }
        else {
            try {
                check()
                if (status.previousHeartbeatOK) {
                    status = ProviderStatus(healthy = true, previousHeartbeatOK = true)
                } else {
                    status = ProviderStatus(healthy = false, previousHeartbeatOK = true)
                }
            } catch (e: Exception) {
                status = ProviderStatus(healthy = false, previousHeartbeatOK = false)
                logger.warn(e) { "Provider $identifier is still unhealthy" }
            }
        }
    }
}

data class ProviderStatus(
    var healthy: Boolean = true,
    var previousHeartbeatOK: Boolean = true,
)

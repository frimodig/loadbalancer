package frimodig.loadbalancer.provider

data class Provider (
    val identifier: String,
    var status: ProviderStatus = ProviderStatus(),
    ) {
        fun get() = identifier

        fun check() {}
}

data class ProviderStatus(
    var healthy: Boolean = true,
    var previousHeartbeatOK: Boolean = true,
)
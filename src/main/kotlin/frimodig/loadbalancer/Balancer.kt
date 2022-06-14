package main.kotlin.frimodig.loadbalancer

import main.kotlin.frimodig.loadbalancer.provider.Provider

const val providerLimit = 10

class Balancer {

    private var algorithm = Algorithm.RANDOM
    private val providers = mutableMapOf<String, Provider>()

    fun get(): String = algorithm.get(providers.values.toList()).get()

    fun setAlgorithm(algorithm: Algorithm) {
        this.algorithm = algorithm
    }

    fun registerProvider(vararg identifier: String) {
        // For now at least, we will ignore duplicate providers
        val newProviderIds = identifier.filter { !providers.containsKey(it) }
        check(providers.size + newProviderIds.size < providerLimit) {
            "Can not register ${newProviderIds.size} new providers. Current providers: ${providers.size}, limit: $providerLimit"
        }
        newProviderIds.forEach { providers[it] = Provider(it) }
    }

    fun removeProvider(vararg identifier: String) {
        identifier.forEach { providers.remove(it) }
    }
}

enum class Algorithm: Selector {
    RANDOM {
        override fun get(providers: List<Provider>) = providers.random()
    },
    ROUND_ROBIN {
        private var previous = -1
        override fun get(providers: List<Provider>): Provider {
            val current = if (previous < providers.size) {
                previous++
            } else -1
            return providers[current]
        }
    }
}

interface Selector {
    fun get(providers: List<Provider>): Provider
}
package frimodig.loadbalancer.provider

// sealed class result?
class Provider(
    private val identifier: String
) {
    fun get() = identifier

    fun check() {}
}
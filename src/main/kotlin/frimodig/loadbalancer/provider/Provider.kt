package frimodig.loadbalancer.provider

// sealed class result?
class Provider(
    val identifier: String
) {
    fun get() = identifier

    fun check() {}
}
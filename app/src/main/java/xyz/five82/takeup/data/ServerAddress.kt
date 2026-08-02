package xyz.five82.takeup.data

import java.net.URI

@JvmInline
value class ServerAddress private constructor(private val uri: URI) {
    override fun toString(): String = uri.toString().removeSuffix("/")

    fun api(path: String): URI {
        require(!path.startsWith('/')) { "API path must be relative" }
        return uri.resolve(path)
    }

    fun stream(path: String): URI {
        val relative = runCatching { URI(path) }
            .getOrElse { throw IllegalArgumentException("Loom returned an invalid stream URL") }
        require(!relative.isAbsolute && relative.rawAuthority == null && path.startsWith('/')) {
            "Loom returned an invalid stream URL"
        }

        val resolved = uri.resolve(relative)
        require(
            resolved.scheme.equals(uri.scheme, ignoreCase = true) &&
                resolved.host.equals(uri.host, ignoreCase = true) &&
                resolved.port == uri.port,
        ) { "Loom returned a stream URL for a different server" }
        return resolved
    }

    companion object {
        fun parse(value: String): ServerAddress {
            val trimmed = value.trim()
            require(trimmed.isNotEmpty()) { "Enter a Loom server URL" }

            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            val parsed = runCatching { URI(withScheme) }
                .getOrElse { throw IllegalArgumentException("Enter a valid Loom server URL") }
            val scheme = parsed.scheme?.lowercase()
            require(scheme == "http" || scheme == "https") {
                "The Loom server URL must use HTTP or HTTPS"
            }
            require(!parsed.isOpaque && parsed.host != null) { "Enter a valid Loom server URL" }
            require(parsed.rawUserInfo == null && parsed.rawQuery == null && parsed.rawFragment == null) {
                "The Loom server URL cannot contain credentials, a query, or a fragment"
            }
            require(parsed.rawPath.isNullOrEmpty() || parsed.rawPath == "/") {
                "The Loom server URL cannot contain a path"
            }

            return ServerAddress(
                URI(scheme, null, parsed.host, parsed.port, "/", null, null),
            )
        }
    }
}

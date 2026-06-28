package com.soneso.smartdemo.coordination

/**
 * Runtime configuration resolved from CLI flags and environment variables.
 *
 * CLI flags take precedence over environment variables. A bearer token is
 * mandatory: the server refuses to start without one rather than running open.
 */
data class ServerConfig(
    val port: Int,
    val token: String,
    val storePath: String? = null,
) {
    companion object {
        const val DEFAULT_PORT = 8787

        /** Environment variable holding the bearer token. */
        const val TOKEN_ENV = "COORDINATION_TOKEN"

        /** Environment variable holding the persistence file path. */
        const val STORE_ENV = "COORDINATION_STORE"

        /** Environment variable holding the port. */
        const val PORT_ENV = "PORT"

        private val KNOWN_FLAGS = setOf("port", "token", "store")

        /**
         * Resolves configuration from process [args] and [environment].
         *
         * Recognised flags: `--port <n>`, `--token <s>`, `--store <path>` (each
         * also accepts the `--flag=value` form). Throws [ConfigException] on an
         * unknown flag, a malformed port, or a missing/empty token.
         */
        fun resolve(args: List<String>, environment: Map<String, String>): ServerConfig {
            val flags = parseFlags(args)

            val portValue = flags["port"] ?: environment[PORT_ENV]
            val port: Int = if (!portValue.isNullOrEmpty()) {
                val parsed = portValue.toIntOrNull()
                if (parsed == null || parsed < 0 || parsed > 65535) {
                    throw ConfigException(
                        "invalid port \"$portValue\": expected an integer in 0..65535"
                    )
                }
                parsed
            } else {
                DEFAULT_PORT
            }

            val tokenValue = flags["token"] ?: environment[TOKEN_ENV]
            if (tokenValue.isNullOrEmpty()) {
                throw ConfigException(
                    "no bearer token configured. Set $TOKEN_ENV or pass --token <value>. " +
                        "The server refuses to start without a token to avoid running open."
                )
            }

            val storeRaw = flags["store"] ?: environment[STORE_ENV]
            val storePath = if (storeRaw.isNullOrEmpty()) null else storeRaw

            return ServerConfig(port = port, token = tokenValue, storePath = storePath)
        }

        private fun parseFlags(args: List<String>): Map<String, String> {
            val flags = mutableMapOf<String, String>()
            var index = 0
            while (index < args.size) {
                val arg = args[index]
                if (!arg.startsWith("--")) {
                    throw ConfigException("unexpected argument \"$arg\"")
                }
                val body = arg.substring(2)
                val name: String
                val value: String
                val eq = body.indexOf('=')
                if (eq >= 0) {
                    name = body.substring(0, eq)
                    value = body.substring(eq + 1)
                } else {
                    name = body
                    if (index + 1 >= args.size) {
                        throw ConfigException("missing value for flag \"--$name\"")
                    }
                    index += 1
                    value = args[index]
                }
                if (name !in KNOWN_FLAGS) {
                    throw ConfigException("unknown flag \"--$name\"")
                }
                flags[name] = value
                index += 1
            }
            return flags
        }
    }
}

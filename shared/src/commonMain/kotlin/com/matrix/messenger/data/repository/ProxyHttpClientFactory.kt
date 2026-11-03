package com.matrix.messenger.data.repository

import com.matrix.messenger.data.model.ProxySettings
import com.matrix.messenger.data.model.ProxyType
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.statement.bodyAsText
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.HttpClientConfig
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Builds a Trixnity-compatible httpClientFactory that routes all Matrix
 * traffic through the user-configured proxy (HTTP or SOCKS5). Proxy
 * credentials apply to HTTP proxies via OkHttp's proxyAuthenticator.
 */
fun createProxyHttpClientFactory(
    proxy: ProxySettings?
): (HttpClientConfig<*>.() -> Unit) -> HttpClient {
    val effective = proxy?.takeIf { it.type != ProxyType.NONE && it.host.isNotBlank() && it.port in 1..65535 }
    return { extraConfig ->
        HttpClient(OkHttp) {
            extraConfig(this)
            // Detect server-side token invalidation (M_UNKNOWN_TOKEN). UIA flows
            // also answer 401, so the body must contain the token error to count.
            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status.value == 401) {
                        val body = runCatching { response.bodyAsText() }.getOrDefault("")
                        if (body.contains("M_UNKNOWN_TOKEN") || body.contains("token is not active")) {
                            TokenInvalidationDetector.trigger()
                        }
                    }
                }
            }
            if (effective != null) {
                engine {
                    val address = InetSocketAddress(effective.host, effective.port)
                    this.proxy = Proxy(
                        when (effective.type) {
                            ProxyType.SOCKS -> Proxy.Type.SOCKS
                            else -> Proxy.Type.HTTP
                        },
                        address
                    )
                    if (!effective.username.isNullOrBlank()) {
                        config {
                            // HTTP proxy authentication (OkHttp-native)
                            proxyAuthenticator { _, response ->
                                response.request.newBuilder()
                                    .header(
                                        "Proxy-Authorization",
                                        okhttp3.Credentials.basic(
                                            effective.username,
                                            effective.password ?: ""
                                        )
                                    )
                                    .build()
                            }
                        }
                    }
                }
            }
        }
    }
}
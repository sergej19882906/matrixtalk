package com.matrix.messenger.data.repository

import com.matrix.messenger.data.model.ProxySettings
import com.matrix.messenger.data.model.ProxyType
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
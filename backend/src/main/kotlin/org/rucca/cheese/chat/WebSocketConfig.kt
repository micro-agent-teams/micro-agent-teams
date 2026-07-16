/*
 *  Description: STOMP-over-WebSocket configuration. JWT authentication is
 *               handled by the handshake interceptor (reads ?token= query
 *               param); messages are published to /topic/thread/{id} by
 *               ThreadService.postMessage via SimpMessagingTemplate.
 *
 *  Author(s):
 *      Nictheboy Li    <nictheboy@outlook.com>
 *
 */

package org.rucca.cheese.chat

import org.rucca.cheese.auth.AuthorizationService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.server.HandshakeInterceptor

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(private val authorizationService: AuthorizationService) :
    WebSocketMessageBrokerConfigurer {

    private val logger = LoggerFactory.getLogger(WebSocketConfig::class.java)

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // /topic is a simple in-memory broker for outgoing broadcasts.
        registry.enableSimpleBroker("/topic")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry
            .addEndpoint("/nt/ws")
            .setAllowedOriginPatterns("*")
            .addInterceptors(
                object : HandshakeInterceptor {
                    override fun beforeHandshake(
                        request: ServerHttpRequest,
                        response: ServerHttpResponse,
                        wsHandler: WebSocketHandler,
                        attributes: MutableMap<String, Any>,
                    ): Boolean {
                        val token =
                            request.uri.query?.let { q ->
                                q.split("&")
                                    .firstOrNull { it.startsWith("token=") }
                                    ?.removePrefix("token=")
                            }
                        if (token == null) {
                            logger.warn("WebSocket handshake rejected: missing token query param")
                            return false
                        }
                        return try {
                            val auth = authorizationService.verify(token)
                            attributes["userId"] = auth.userId
                            true
                        } catch (e: Exception) {
                            logger.warn("WebSocket handshake rejected: {}", e.message)
                            false
                        }
                    }

                    override fun afterHandshake(
                        request: ServerHttpRequest,
                        response: ServerHttpResponse,
                        wsHandler: WebSocketHandler,
                        exception: Exception?,
                    ) {}
                }
            )
    }
}

package com.trustify.rocketchat.service;

import com.trustify.rocketchat.config.RocketChatProperties;
import com.trustify.rocketchat.model.RocketChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RocketChatMessageService {

    private final WebClient.Builder webClientBuilder;
    private final RocketChatProperties properties;
    private final RocketChatAuthService authService;

    /**
     * Sends a message to a Rocket.Chat room.
     *
     * @param roomId  the ID of the room to send the message to
     * @param message the message text to send
     * @return Mono<RocketChatMessage> the sent message
     */
    public Mono<RocketChatMessage> sendMessage(String roomId, String message) {
        log.debug("Sending message to room {}", roomId);

        // Ensure we're authenticated
        if (!authService.isAuthenticated()) {
            return authService.login().flatMap(auth -> sendMessageInternal(roomId, message));
        }

        return sendMessageInternal(roomId, message);
    }

    private Mono<RocketChatMessage> sendMessageInternal(String roomId, String message) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("roomId", roomId);
        requestBody.put("text", message);

        return webClientBuilder.build()
                .post()
                .uri(properties.getUrl() + "/chat.postMessage")
                .header("X-Auth-Token", properties.getAuthToken())
                .header("X-User-Id", properties.getUserId())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    if (response.containsKey("message")) {
                        log.debug("Message sent successfully");
                        // Convert the response to a RocketChatMessage
                        // In a real implementation, you would map the response to the RocketChatMessage class
                        return Mono.just(new RocketChatMessage());
                    } else {
                        log.error("Failed to send message: {}", response);
                        return Mono.error(new RuntimeException("Failed to send message"));
                    }
                })
                .doOnError(error -> log.error("Error sending message", error));
    }

    /**
     * Gets messages from a Rocket.Chat room.
     *
     * @param roomId the ID of the room to get messages from
     * @param limit  the maximum number of messages to retrieve
     * @return Flux<RocketChatMessage> the messages
     */
    public Flux<RocketChatMessage> getMessages(String roomId, int limit) {
        log.debug("Getting messages from room {}", roomId);

        // Ensure we're authenticated
        if (!authService.isAuthenticated()) {
            return authService.login().flatMapMany(auth -> getMessagesInternal(roomId, limit));
        }

        return getMessagesInternal(roomId, limit);
    }

    private Flux<RocketChatMessage> getMessagesInternal(String roomId, int limit) {
        URI uri = UriComponentsBuilder.fromHttpUrl(properties.getUrl())
                .path("/channels.messages")
                .queryParam("roomId", roomId)
                .queryParam("count", limit)
                .build()
                .toUri();
        return webClientBuilder.build()
                .get()
                .uri(uri)
                .header("X-Auth-Token", properties.getAuthToken())
                .header("X-User-Id", properties.getUserId())
                .retrieve()
                .bodyToMono(Map.class)
                .flatMapMany(response -> {
                    if (response.containsKey("messages")) {
                        log.debug("Retrieved {} messages", ((List) response.get("messages")).size());
                        // In a real implementation, you would map the response to a list of RocketChatMessage objects
                        return Flux.<RocketChatMessage>empty();
                    } else {
                        log.error("Failed to retrieve messages: {}", response);
                        return Flux.error(new RuntimeException("Failed to retrieve messages"));
                    }
                })
                .doOnError(error -> log.error("Error retrieving messages", error));
    }
}

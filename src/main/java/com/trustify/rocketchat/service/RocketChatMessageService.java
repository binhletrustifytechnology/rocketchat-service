package com.trustify.rocketchat.service;

import com.trustify.rocketchat.config.RocketChatProperties;
import com.trustify.rocketchat.model.RocketChatEndpoint;
import com.trustify.rocketchat.model.RocketChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
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
                .uri(properties.getUrl() + RocketChatEndpoint.CHAT_POST_MESSAGE.getPath())
                .header("X-Auth-Token", properties.getAuthToken())
                .header("X-User-Id", properties.getUserId())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    if (response.containsKey("message")) {
                        log.debug("Message sent successfully");
                        Map<String, Object> messageData = (Map<String, Object>) response.get("message");

                        RocketChatMessage.User user = null;
                        if (messageData.containsKey("u")) {
                            Map<String, Object> userData = (Map<String, Object>) messageData.get("u");
                            user = new RocketChatMessage.User(
                                    (String) userData.get("_id"),
                                    (String) userData.get("username"),
                                    (String) userData.get("name")
                            );
                        }

                        return Mono.just(RocketChatMessage.builder()
                                .id((String) messageData.get("_id"))
                                .roomId((String) messageData.get("rid"))
                                .message((String) messageData.get("msg"))
                                .timestamp(messageData.containsKey("ts") ? 
                                        Instant.parse((String) messageData.get("ts")) : null)
                                .user(user)
                                .build());
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
                .path(RocketChatEndpoint.CHANNELS_MESSAGES.getPath())
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
                        List<Map<String, Object>> messages = (List<Map<String, Object>>) response.get("messages");
                        log.debug("Retrieved {} messages", messages.size());
                        return Flux.fromIterable(messages)
                                .map(messageData -> {
                                    RocketChatMessage.User user = null;
                                    if (messageData.containsKey("u")) {
                                        Map<String, Object> userData = (Map<String, Object>) messageData.get("u");
                                        user = new RocketChatMessage.User(
                                                (String) userData.get("_id"),
                                                (String) userData.get("username"),
                                                (String) userData.get("name")
                                        );
                                    }

                                    return RocketChatMessage.builder()
                                            .id((String) messageData.get("_id"))
                                            .roomId((String) messageData.get("rid"))
                                            .message((String) messageData.get("msg"))
                                            .timestamp(messageData.containsKey("ts") ? 
                                                    Instant.parse((String) messageData.get("ts")) : null)
                                            .user(user)
                                            .build();
                                });
                    } else {
                        log.error("Failed to retrieve messages: {}", response);
                        return Flux.error(new RuntimeException("Failed to retrieve messages"));
                    }
                })
                .doOnError(error -> log.error("Error retrieving messages", error));
    }

    /**
     * Searches for messages containing the specified text.
     *
     * @param searchText the text to search for
     * @param roomId the ID of the room to search in (optional, if null searches all rooms)
     * @return Flux<RocketChatMessage> the messages matching the search criteria
     */
    public Flux<RocketChatMessage> searchMessages(String searchText, String roomId) {
        if (roomId == null) {
            log.debug("Searching for messages containing: {} in all rooms", searchText);
        } else {
            log.debug("Searching for messages containing: {} in room: {}", searchText, roomId);
        }

        // Ensure we're authenticated
        if (!authService.isAuthenticated()) {
            return authService.login().flatMapMany(auth -> searchMessagesInternal(searchText, roomId));
        }

        return searchMessagesInternal(searchText, roomId);
    }

    private Flux<RocketChatMessage> searchMessagesInternal(String searchText, String roomId) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(properties.getUrl())
                .path(RocketChatEndpoint.CHAT_SEARCH.getPath())
                .queryParam("searchText", searchText);

        // Only include roomId parameter if it's not null
        if (roomId != null) {
            uriBuilder.queryParam("roomId", roomId);
        }

        URI uri = uriBuilder.build().toUri();

        return webClientBuilder.build()
                .get()
                .uri(uri)
                .header("X-Auth-Token", properties.getAuthToken())
                .header("X-User-Id", properties.getUserId())
                .retrieve()
                .bodyToMono(Map.class)
                .flatMapMany(response -> {
                    if (response.containsKey("messages")) {
                        List<Map<String, Object>> messages = (List<Map<String, Object>>) response.get("messages");
                        log.debug("Found {} messages matching search criteria", messages.size());
                        return Flux.fromIterable(messages)
                                .map(messageData -> {
                                    RocketChatMessage.User user = null;
                                    if (messageData.containsKey("u")) {
                                        Map<String, Object> userData = (Map<String, Object>) messageData.get("u");
                                        user = new RocketChatMessage.User(
                                                (String) userData.get("_id"),
                                                (String) userData.get("username"),
                                                (String) userData.get("name")
                                        );
                                    }

                                    return RocketChatMessage.builder()
                                            .id((String) messageData.get("_id"))
                                            .roomId((String) messageData.get("rid"))
                                            .message((String) messageData.get("msg"))
                                            .timestamp(messageData.containsKey("ts") ? 
                                                    Instant.parse((String) messageData.get("ts")) : null)
                                            .user(user)
                                            .build();
                                });
                    } else {
                        log.error("Failed to search messages: {}", response);
                        return Flux.error(new RuntimeException("Failed to search messages"));
                    }
                })
                .doOnError(error -> log.error("Error searching messages", error));
    }
}

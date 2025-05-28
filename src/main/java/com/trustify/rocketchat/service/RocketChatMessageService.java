package com.trustify.rocketchat.service;

import com.trustify.rocketchat.config.RocketChatProperties;
import com.trustify.rocketchat.model.RocketChatEndpoint;
import com.trustify.rocketchat.model.RocketChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * Sends a message with file attachments to a Rocket.Chat room.
     *
     * @param roomId  the ID of the room to send the message to
     * @param message the message text to send
     * @param files   the list of files to upload
     * @return Mono<RocketChatMessage> the sent message
     */
    public Mono<RocketChatMessage> sendMessage(String roomId, String message, List<MultipartFile> files) {
        log.debug("Sending message with {} files to room {}", files != null ? files.size() : 0, roomId);

        // If no files or empty list, use the standard message sending method
        if (files == null || files.isEmpty()) {
            return sendMessage(roomId, message);
        }

        // Ensure we're authenticated
        if (!authService.isAuthenticated()) {
            return authService.login().flatMap(auth -> sendMessageWithFilesInternal(roomId, message, files));
        }

        return sendMessageWithFilesInternal(roomId, message, files);
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
                .flatMap(this::processMessageResponse)
                .doOnError(error -> log.error("Error sending message", error));
    }

    private Mono<RocketChatMessage> sendMessageWithFilesInternal(String roomId, String message, List<MultipartFile> files) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        // Add message text
        bodyBuilder.part("msg", message);

        // Add room ID
        bodyBuilder.part("roomId", roomId);

        // Add the first file (Rocket.Chat API only supports one file per request)
        if (!files.isEmpty()) {
            MultipartFile file = files.get(0);
            try {
                bodyBuilder.part("file", file.getResource())
                        .filename(file.getOriginalFilename())
                        .contentType(MediaType.parseMediaType(file.getContentType()));
            } catch (Exception e) {
                log.error("Error adding file to request", e);
                return Mono.error(new RuntimeException("Failed to process file upload: " + e.getMessage()));
            }
        }

        return webClientBuilder.build()
                .post()
                .uri(properties.getUrl() + RocketChatEndpoint.ROOMS_UPLOAD.getPath() + "/" + roomId)
                .header("X-Auth-Token", properties.getAuthToken())
                .header("X-User-Id", properties.getUserId())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(this::processUploadResponse)
                .doOnError(error -> log.error("Error uploading file to room", error));
    }

    private Mono<RocketChatMessage> processMessageResponse(Map<String, Object> response) {
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
    }

    private Mono<RocketChatMessage> processUploadResponse(Map<String, Object> response) {
        if (response.containsKey("success") && (Boolean) response.get("success")) {
            log.debug("File uploaded successfully");

            // Extract the message data from the response
            Map<String, Object> messageData = null;
            if (response.containsKey("message")) {
                messageData = (Map<String, Object>) response.get("message");
            }

            if (messageData == null) {
                log.error("No message data in upload response: {}", response);
                return Mono.error(new RuntimeException("Failed to process upload response"));
            }

            // Extract user data
            RocketChatMessage.User user = null;
            if (messageData.containsKey("u")) {
                Map<String, Object> userData = (Map<String, Object>) messageData.get("u");
                user = new RocketChatMessage.User(
                        (String) userData.get("_id"),
                        (String) userData.get("username"),
                        (String) userData.get("name")
                );
            }

            // Extract attachment data
            List<RocketChatMessage.Attachment> attachments = null;
            if (messageData.containsKey("attachments")) {
                List<Map<String, Object>> attachmentDataList = (List<Map<String, Object>>) messageData.get("attachments");
                attachments = attachmentDataList.stream()
                        .map(attachmentData -> RocketChatMessage.Attachment.builder()
                                .title((String) attachmentData.get("title"))
                                .type((String) attachmentData.get("type"))
                                .description((String) attachmentData.get("description"))
                                .titleLink((String) attachmentData.get("title_link"))
                                .titleLinkDownload(attachmentData.containsKey("title_link_download") ? 
                                        (Boolean) attachmentData.get("title_link_download") : false)
                                .imageUrl((String) attachmentData.get("image_url"))
                                .imageType((String) attachmentData.get("image_type"))
                                .imageSize(attachmentData.containsKey("image_size") ? 
                                        ((Number) attachmentData.get("image_size")).longValue() : null)
                                .build())
                        .collect(Collectors.toList());
            }

            return Mono.just(RocketChatMessage.builder()
                    .id((String) messageData.get("_id"))
                    .roomId((String) messageData.get("rid"))
                    .message((String) messageData.get("msg"))
                    .timestamp(messageData.containsKey("ts") ? 
                            Instant.parse((String) messageData.get("ts")) : null)
                    .user(user)
                    .attachments(attachments)
                    .build());
        } else {
            log.error("Failed to upload file: {}", response);
            return Mono.error(new RuntimeException("Failed to upload file"));
        }
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

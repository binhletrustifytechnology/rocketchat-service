package com.trustify.rocketchat.service;

import com.trustify.rocketchat.config.RocketChatProperties;
import com.trustify.rocketchat.model.RocketChatRoom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RocketChatRoomService {

    private final WebClient.Builder webClientBuilder;
    private final RocketChatProperties properties;
    private final RocketChatAuthService authService;

    /**
     * Gets a list of public channels.
     *
     * @return Flux<RocketChatRoom> the channels
     */
    public Flux<RocketChatRoom> getPublicChannels() {
        log.debug("Getting public channels");

        // Ensure we're authenticated
        if (!authService.isAuthenticated()) {
            return authService.login().flatMapMany(auth -> getPublicChannelsInternal());
        }

        return getPublicChannelsInternal();
    }

    private Flux<RocketChatRoom> getPublicChannelsInternal() {
        return webClientBuilder.build()
                .get()
                .uri(properties.getUrl() + "/channels.list")
                .header("X-Auth-Token", properties.getAuthToken())
                .header("X-User-Id", properties.getUserId())
                .retrieve()
                .bodyToMono(Map.class)
                .flatMapMany(response -> {
                    if (response.containsKey("channels")) {
                        log.debug("Retrieved {} channels", response.get("count"));

                        // Map the response to a list of RocketChatRoom objects
                        List<Map<String, Object>> channels = (List<Map<String, Object>>) response.get("channels");
                        return Flux.fromIterable(channels)
                                .map(this::mapToRocketChatRoom);
                    } else {
                        log.error("Failed to retrieve channels: {}", response);
                        return Flux.error(new RuntimeException("Failed to retrieve channels"));
                    }
                })
                .doOnError(error -> log.error("Error retrieving channels", error));
    }

    /**
     * Creates a new channel.
     *
     * @param name        the name of the channel
     * @param members     the initial members of the channel (usernames)
     * @param readOnly    whether the channel is read-only
     * @param description the description of the channel
     * @return Mono<RocketChatRoom> the created channel
     */
    public Mono<RocketChatRoom> createChannel(String name, String[] members, boolean readOnly, String description) {
        log.debug("Creating channel {}", name);

        // Ensure we're authenticated
        if (!authService.isAuthenticated()) {
            return authService.login().flatMap(auth -> createChannelInternal(name, members, readOnly, description));
        }

        return createChannelInternal(name, members, readOnly, description);
    }

    private Mono<RocketChatRoom> createChannelInternal(String name, String[] members, boolean readOnly, String description) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", name);
        requestBody.put("members", members);
        requestBody.put("readOnly", readOnly);

        if (description != null && !description.isEmpty()) {
            requestBody.put("description", description);
        }

        return webClientBuilder.build()
                .post()
                .uri(properties.getUrl() + "/channels.create")
                .header("X-Auth-Token", properties.getAuthToken())
                .header("X-User-Id", properties.getUserId())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    if (response.containsKey("channel")) {
                        log.debug("Channel created successfully");
                        Map<String, Object> channelData = (Map<String, Object>) response.get("channel");
                        return Mono.just(mapToRocketChatRoom(channelData));
                    } else {
                        log.error("Failed to create channel: {}", response);
                        return Mono.error(new RuntimeException("Failed to create channel"));
                    }
                })
                .doOnError(error -> log.error("Error creating channel", error));
    }

    /**
     * Gets information about a channel.
     *
     * @param roomId the ID of the room
     * @return Mono<RocketChatRoom> the room information
     */
    public Mono<RocketChatRoom> getChannelInfo(String roomId) {
        log.debug("Getting info for channel {}", roomId);

        // Ensure we're authenticated
        if (!authService.isAuthenticated()) {
            return authService.login().flatMap(auth -> getChannelInfoInternal(roomId));
        }

        return getChannelInfoInternal(roomId);
    }

    private Mono<RocketChatRoom> getChannelInfoInternal(String roomId) {
        return webClientBuilder.build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(properties.getUrl() + "/channels.info")
                        .queryParam("roomId", roomId)
                        .build())
                .header("X-Auth-Token", properties.getAuthToken())
                .header("X-User-Id", properties.getUserId())
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    if (response.containsKey("channel")) {
                        log.debug("Retrieved channel info successfully");
                        Map<String, Object> channelData = (Map<String, Object>) response.get("channel");
                        return Mono.just(mapToRocketChatRoom(channelData));
                    } else {
                        log.error("Failed to retrieve channel info: {}", response);
                        return Mono.error(new RuntimeException("Failed to retrieve channel info"));
                    }
                })
                .doOnError(error -> log.error("Error retrieving channel info", error));
    }

    /**
     * Maps a channel data map from the Rocket.Chat API to a RocketChatRoom object.
     *
     * @param channelData the channel data from the API
     * @return the mapped RocketChatRoom object
     */
    private RocketChatRoom mapToRocketChatRoom(Map<String, Object> channelData) {
        RocketChatRoom room = new RocketChatRoom();

        if (channelData.containsKey("_id")) {
            room.setId((String) channelData.get("_id"));
        }

        if (channelData.containsKey("name")) {
            room.setName((String) channelData.get("name"));
        }

        if (channelData.containsKey("t")) {
            room.setType((String) channelData.get("t"));
        }

        if (channelData.containsKey("u")) {
            Map<String, Object> userData = (Map<String, Object>) channelData.get("u");
            RocketChatRoom.User user = new RocketChatRoom.User();
            user.setId((String) userData.get("_id"));
            user.setUsername((String) userData.get("username"));
            room.setCreator(user);
        }

        if (channelData.containsKey("topic")) {
            room.setTopic((String) channelData.get("topic"));
        }

        if (channelData.containsKey("description")) {
            room.setDescription((String) channelData.get("description"));
        }

        if (channelData.containsKey("ro")) {
            room.setReadOnly((Boolean) channelData.get("ro"));
        }

        if (channelData.containsKey("default")) {
            room.setDefaultRoom((Boolean) channelData.get("default"));
        }

        if (channelData.containsKey("ts")) {
            String tsString = (String) channelData.get("ts");
            room.setCreatedAt(Instant.parse(tsString));
        }

        if (channelData.containsKey("_updatedAt")) {
            String updatedAtString = (String) channelData.get("_updatedAt");
            room.setUpdatedAt(Instant.parse(updatedAtString));
        }

        return room;
    }
}

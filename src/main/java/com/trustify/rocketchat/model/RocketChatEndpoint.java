package com.trustify.rocketchat.model;

/**
 * Enum representing Rocket.Chat API endpoints.
 */
public enum RocketChatEndpoint {

    // Channel endpoints
    CHANNELS_LIST("/channels.list"),
    CHANNELS_CREATE("/channels.create"),
    CHANNELS_DELETE("/channels.delete"),
    CHANNELS_INFO("/channels.info"),
    CHANNELS_MESSAGES("/channels.messages"),
    CHAT_SEARCH("/chat.search"),
    CHAT_POST_MESSAGE("/chat.postMessage"),
    ROOMS_UPLOAD("/rooms.upload"),
    ROOMS_MEDIA("/rooms.media");

    private final String path;

    RocketChatEndpoint(String path) {
        this.path = path;
    }

    /**
     * Gets the path for this endpoint.
     *
     * @return the path
     */
    public String getPath() {
        return path;
    }
}

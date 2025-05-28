package com.trustify.rocketchat.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RocketChatMessage {
    @JsonProperty("_id")
    private String id;

    @JsonProperty("rid")
    private String roomId;

    @JsonProperty("msg")
    private String message;

    @JsonProperty("ts")
    private Instant timestamp;

    @JsonProperty("u")
    private User user;

    @JsonProperty("attachments")
    private List<Attachment> attachments;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        @JsonProperty("_id")
        private String id;

        @JsonProperty("username")
        private String username;

        @JsonProperty("name")
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Attachment {
        @JsonProperty("title")
        private String title;

        @JsonProperty("type")
        private String type;

        @JsonProperty("description")
        private String description;

        @JsonProperty("title_link")
        private String titleLink;

        @JsonProperty("title_link_download")
        private boolean titleLinkDownload;

        @JsonProperty("image_url")
        private String imageUrl;

        @JsonProperty("image_type")
        private String imageType;

        @JsonProperty("image_size")
        private Long imageSize;
    }
}

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
public class RocketChatRoom {
    @JsonProperty("_id")
    private String id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("t")
    private String type; // c: channel, d: direct message, p: private group
    
    @JsonProperty("u")
    private User creator;
    
    @JsonProperty("topic")
    private String topic;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("ro")
    private boolean readOnly;
    
    @JsonProperty("default")
    private boolean defaultRoom;
    
    @JsonProperty("ts")
    private Instant createdAt;
    
    @JsonProperty("_updatedAt")
    private Instant updatedAt;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        @JsonProperty("_id")
        private String id;
        
        @JsonProperty("username")
        private String username;
    }
}
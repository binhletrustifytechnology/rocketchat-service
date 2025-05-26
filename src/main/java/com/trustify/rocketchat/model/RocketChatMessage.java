package com.trustify.rocketchat.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

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
}
package com.trustify.rocketchat.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RocketChatAuthResponse {
    private String status;
    private Data data;
    
    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Data {
        @JsonProperty("authToken")
        private String authToken;
        
        @JsonProperty("userId")
        private String userId;
        
        @JsonProperty("me")
        private User me;
    }
    
    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        @JsonProperty("_id")
        private String id;
        
        @JsonProperty("username")
        private String username;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("email")
        private String email;
    }
}
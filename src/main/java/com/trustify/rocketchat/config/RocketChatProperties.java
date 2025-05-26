package com.trustify.rocketchat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rocketchat.api")
public class RocketChatProperties {
    private String url;
    private String user;
    private String password;
    private String authToken;
    private String userId;
}
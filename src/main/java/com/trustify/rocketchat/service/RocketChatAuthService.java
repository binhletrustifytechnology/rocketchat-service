package com.trustify.rocketchat.service;

import com.trustify.rocketchat.config.RocketChatProperties;
import com.trustify.rocketchat.model.RocketChatAuthRequest;
import com.trustify.rocketchat.model.RocketChatAuthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class RocketChatAuthService {

    private final WebClient.Builder webClientBuilder;
    private final RocketChatProperties properties;
    
    /**
     * Authenticates with Rocket.Chat API and returns the authentication response.
     * 
     * @return Mono<RocketChatAuthResponse> containing auth token and user ID
     */
    public Mono<RocketChatAuthResponse> login() {
        log.debug("Authenticating with Rocket.Chat API at {}", properties.getUrl());
        
        RocketChatAuthRequest request = RocketChatAuthRequest.builder()
                .username(properties.getUser())
                .password(properties.getPassword())
                .build();
        
        return webClientBuilder.build()
                .post()
                .uri(properties.getUrl() + "/login")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RocketChatAuthResponse.class)
                .doOnSuccess(response -> {
                    log.debug("Successfully authenticated with Rocket.Chat API");
                    // Update properties with auth token and user ID for future requests
                    properties.setAuthToken(response.getData().getAuthToken());
                    properties.setUserId(response.getData().getUserId());
                })
                .doOnError(error -> log.error("Failed to authenticate with Rocket.Chat API", error));
    }
    
    /**
     * Checks if we have valid authentication credentials.
     * 
     * @return true if we have an auth token and user ID
     */
    public boolean isAuthenticated() {
        return properties.getAuthToken() != null && !properties.getAuthToken().isEmpty() &&
               properties.getUserId() != null && !properties.getUserId().isEmpty();
    }
}
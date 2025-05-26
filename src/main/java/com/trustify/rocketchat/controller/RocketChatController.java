package com.trustify.rocketchat.controller;

import com.trustify.rocketchat.model.RocketChatMessage;
import com.trustify.rocketchat.model.RocketChatRoom;
import com.trustify.rocketchat.service.RocketChatAuthService;
import com.trustify.rocketchat.service.RocketChatMessageService;
import com.trustify.rocketchat.service.RocketChatRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rocketchat")
@RequiredArgsConstructor
public class RocketChatController {

    private final RocketChatAuthService authService;
    private final RocketChatMessageService messageService;
    private final RocketChatRoomService roomService;

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> login() {
        return authService.login()
                .map(response -> {
                    log.info("Successfully authenticated with Rocket.Chat");
                    return ResponseEntity.ok(Map.of(
                            "status", "success",
                            "message", "Successfully authenticated with Rocket.Chat"
                    ));
                })
                .onErrorResume(error -> {
                    log.error("Failed to authenticate with Rocket.Chat", error);
                    return Mono.just(ResponseEntity.badRequest().body(Map.of(
                            "status", "error",
                            "message", "Failed to authenticate with Rocket.Chat: " + error.getMessage()
                    )));
                });
    }

    @GetMapping("/channels")
    public Flux<RocketChatRoom> getChannels() {
        return roomService.getPublicChannels();
    }

    @GetMapping("/channels/{roomId}")
    public Mono<RocketChatRoom> getChannelInfo(@PathVariable String roomId) {
        return roomService.getChannelInfo(roomId);
    }

    @PostMapping("/channels")
    public Mono<RocketChatRoom> createChannel(
            @RequestParam @NotBlank String name,
            @RequestParam(required = false) String[] members,
            @RequestParam(defaultValue = "false") boolean readOnly,
            @RequestParam(required = false) String description) {
        
        return roomService.createChannel(name, members != null ? members : new String[0], readOnly, description);
    }

    @GetMapping("/channels/{roomId}/messages")
    public Flux<RocketChatMessage> getMessages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "50") int limit) {
        
        return messageService.getMessages(roomId, limit);
    }

    @PostMapping("/channels/{roomId}/messages")
    public Mono<RocketChatMessage> sendMessage(
            @PathVariable String roomId,
            @RequestBody @Valid MessageRequest request) {
        
        return messageService.sendMessage(roomId, request.getMessage());
    }

    // Request DTOs
    public static class MessageRequest {
        @NotBlank
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
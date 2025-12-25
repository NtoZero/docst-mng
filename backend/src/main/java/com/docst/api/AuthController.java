package com.docst.api;

import com.docst.api.ApiModels.AuthTokenResponse;
import com.docst.api.ApiModels.UserResponse;
import com.docst.domain.User;
import com.docst.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/local/login")
    public ResponseEntity<AuthTokenResponse> login(@RequestBody LoginRequest request) {
        User user = userService.createOrUpdateLocalUser(request.email(), request.displayName());
        // TODO: Generate proper JWT token
        String token = "dev-token-" + user.getId();
        AuthTokenResponse response = new AuthTokenResponse(token, "Bearer", 3600);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@RequestHeader(value = "Authorization", required = false) String auth) {
        // TODO: Parse JWT and get actual user
        // For now, return a placeholder user for development
        UserResponse response = new UserResponse(
                UUID.randomUUID(),
                "LOCAL",
                "local-user",
                "local@example.com",
                "Local User",
                java.time.Instant.now()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // TODO: Invalidate token if using server-side session
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(String email, String displayName) {}
}

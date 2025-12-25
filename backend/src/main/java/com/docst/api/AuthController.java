package com.docst.api;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docst.api.ApiModels.AuthTokenResponse;
import com.docst.api.ApiModels.UserResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  @PostMapping("/local/login")
  public ResponseEntity<AuthTokenResponse> login(@RequestBody LoginRequest request) {
    AuthTokenResponse response = new AuthTokenResponse("dev-token-" + UUID.randomUUID(), "Bearer", 3600);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/me")
  public ResponseEntity<UserResponse> me() {
    UserResponse response = new UserResponse(
        UUID.randomUUID(),
        "LOCAL",
        "local-user",
        "local@example.com",
        "Local User",
        Instant.now()
    );
    return ResponseEntity.ok(response);
  }

  public record LoginRequest(String email, String displayName) {}
}

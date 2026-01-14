package com.docst.admin.api;

import com.docst.auth.PasswordValidator;
import com.docst.user.User;
import com.docst.user.User.AuthProvider;
import com.docst.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 시스템 초기 설정 컨트롤러.
 * 최초 관리자 계정 생성 등 일회성 초기화 작업을 제공한다.
 */
@Tag(name = "Setup", description = "시스템 초기 설정 API")
@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
@Slf4j
public class SetupController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;

    /**
     * 시스템 초기화 상태 확인.
     * LOCAL 사용자가 존재하는지 확인하여 초기화 가능 여부를 반환한다.
     *
     * @return 초기화 가능 여부 및 메시지
     */
    @Operation(summary = "시스템 초기화 상태 확인", description = "시스템 초기화 필요 여부를 확인합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/status")
    public ResponseEntity<SetupStatusResponse> getSetupStatus() {
        long localUserCount = userRepository.findAll().stream()
                .filter(User::isLocalUser)
                .count();

        boolean needsSetup = localUserCount == 0;

        return ResponseEntity.ok(new SetupStatusResponse(
                needsSetup,
                needsSetup ? "System is not initialized. Please create an admin account."
                        : "System is already initialized.",
                localUserCount
        ));
    }

    /**
     * 최초 관리자 계정 생성.
     * LOCAL 사용자가 하나도 없을 때만 실행 가능하다.
     *
     * @param request 관리자 계정 생성 요청
     * @return 생성된 관리자 정보 또는 에러 메시지
     */
    @Operation(summary = "최초 관리자 계정 생성", description = "시스템 초기화 시 최초 관리자 계정을 생성합니다. LOCAL 사용자가 없을 때만 실행 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "관리자 계정 생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (비밀번호 검증 실패 등)"),
            @ApiResponse(responseCode = "409", description = "이미 초기화됨")
    })
    @PostMapping("/initialize")
    public ResponseEntity<?> initialize(@Valid @RequestBody InitializeRequest request) {
        // LOCAL 사용자가 이미 존재하는지 확인
        boolean localUserExists = userRepository.findAll().stream()
                .anyMatch(User::isLocalUser);

        if (localUserExists) {
            log.warn("Setup initialization rejected: LOCAL user already exists");
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "ALREADY_INITIALIZED",
                            "message", "System is already initialized. Cannot create admin account."
                    ));
        }

        // 비밀번호 복잡도 검증
        try {
            passwordValidator.validate(request.password());
        } catch (IllegalArgumentException e) {
            log.warn("Setup initialization rejected: invalid password - {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "INVALID_PASSWORD",
                            "message", e.getMessage()
                    ));
        }

        // 관리자 계정 생성
        try {
            User admin = new User(
                    AuthProvider.LOCAL,
                    request.email(),
                    request.email(),
                    request.displayName()
            );

            String hashedPassword = passwordEncoder.encode(request.password());
            admin.setPasswordHash(hashedPassword);

            User savedAdmin = userRepository.save(admin);

            log.info("Initial admin account created successfully: {}", request.email());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new InitializeResponse(
                            savedAdmin.getId().toString(),
                            savedAdmin.getEmail(),
                            savedAdmin.getDisplayName(),
                            "Admin account created successfully. Please login with your credentials."
                    ));
        } catch (Exception e) {
            log.error("Failed to create initial admin account", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "CREATION_FAILED",
                            "message", "Failed to create admin account. Please contact support."
                    ));
        }
    }

    /**
     * 시스템 초기화 상태 응답.
     */
    public record SetupStatusResponse(
            boolean needsSetup,
            String message,
            long existingUserCount
    ) {}

    /**
     * 관리자 계정 생성 요청.
     */
    public record InitializeRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
            String password,

            @NotBlank(message = "Display name is required")
            @Size(min = 1, max = 100, message = "Display name must be between 1 and 100 characters")
            String displayName
    ) {}

    /**
     * 관리자 계정 생성 응답.
     */
    public record InitializeResponse(
            String userId,
            String email,
            String displayName,
            String message
    ) {}
}

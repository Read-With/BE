package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.dto.auth.TokenResponseDTO;
import com.kw.readwith.dto.auth.UserInfoResponseDTO;
import com.kw.readwith.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "인증 관련 API")
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "Refresh Token을 사용하여 새로운 Access Token을 발급받습니다.")
    public ApiResponse<TokenResponseDTO> refreshToken(
            @Parameter(description = "Refresh Token", required = true)
            @RequestHeader("Refresh-Token") String refreshToken) {
        
        TokenResponseDTO response = authService.refreshToken(refreshToken);
        return ApiResponse.onSuccess(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "사용자를 로그아웃하고 Refresh Token을 무효화합니다.")
    public ApiResponse<String> logout(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        authService.logout(userId);
        return ApiResponse.onSuccess("로그아웃이 완료되었습니다.");
    }

    @GetMapping("/me")
    @Operation(summary = "사용자 정보 조회", description = "현재 로그인한 사용자의 정보를 조회합니다.")
    public ApiResponse<UserInfoResponseDTO> getUserInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserInfoResponseDTO response = authService.getUserInfo(userId);
        return ApiResponse.onSuccess(response);
    }

    @DeleteMapping("/account")
    @Operation(summary = "회원 탈퇴", description = "사용자 계정을 삭제합니다.")
    public ApiResponse<String> deleteAccount(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        authService.deleteAccount(userId);
        return ApiResponse.onSuccess("회원 탈퇴가 완료되었습니다.");
    }

    @GetMapping("/status")
    @Operation(summary = "인증 상태 확인", description = "현재 사용자의 인증 상태를 확인합니다.")
    public ApiResponse<String> checkAuthStatus(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            Long userId = (Long) authentication.getPrincipal();
            return ApiResponse.onSuccess("인증된 사용자입니다. 사용자 ID: " + userId);
        } else {
            return ApiResponse.onSuccess("인증되지 않은 사용자입니다.");
        }
    }
}

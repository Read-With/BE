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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "토큰 관리와 현재 사용자 계정 정보를 다루는 API입니다.")
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/refresh")
    @Operation(summary = "액세스 토큰 재발급", description = "Refresh Token으로 새로운 Access Token을 발급합니다.")
    public ApiResponse<TokenResponseDTO> refreshToken(
            @Parameter(description = "요청 헤더 `Refresh-Token`에 넣어 보낼 refresh token", required = true)
            @RequestHeader("Refresh-Token") String refreshToken) {

        TokenResponseDTO response = authService.refreshToken(refreshToken);
        return ApiResponse.onSuccess(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "현재 사용자의 refresh token을 무효화합니다.")
    public ApiResponse<String> logout(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        authService.logout(userId);
        return ApiResponse.onSuccess("로그아웃이 완료되었습니다.");
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 정보를 조회합니다.")
    public ApiResponse<UserInfoResponseDTO> getUserInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserInfoResponseDTO response = authService.getUserInfo(userId);
        return ApiResponse.onSuccess(response);
    }

    @DeleteMapping("/account")
    @Operation(summary = "회원 탈퇴", description = "현재 로그인한 사용자의 계정을 삭제합니다.")
    public ApiResponse<String> deleteAccount(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        authService.deleteAccount(userId);
        return ApiResponse.onSuccess("회원 탈퇴가 완료되었습니다.");
    }

    @GetMapping("/status")
    @Operation(summary = "인증 상태 확인", description = "현재 요청의 인증 여부를 간단히 확인합니다.")
    public ApiResponse<String> checkAuthStatus(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            Long userId = (Long) authentication.getPrincipal();
            return ApiResponse.onSuccess("인증된 사용자입니다. 사용자 ID: " + userId);
        } else {
            return ApiResponse.onSuccess("인증되지 않은 사용자입니다.");
        }
    }
}

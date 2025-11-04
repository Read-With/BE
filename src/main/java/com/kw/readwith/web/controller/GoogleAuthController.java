package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.domain.User;
import com.kw.readwith.dto.auth.GoogleLoginRequestDTO;
import com.kw.readwith.dto.auth.TokenResponseDTO;
import com.kw.readwith.dto.auth.UserInfoResponseDTO;
import com.kw.readwith.service.GoogleOAuth2Service;
import com.kw.readwith.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/google")
@RequiredArgsConstructor
@Tag(name = "Google Authentication", description = "Google OAuth2 인증 관련 API")
@Slf4j
public class GoogleAuthController {

    private final GoogleOAuth2Service googleOAuth2Service;
    private final JwtUtil jwtUtil;

    @PostMapping
    @Operation(
        summary = "Google OAuth2 로그인", 
        description = "Google OAuth2 인증 코드를 사용하여 로그인합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "로그인 성공",
            content = @Content(schema = @Schema(implementation = TokenResponseDTO.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "잘못된 요청 (인증 코드 누락 또는 유효하지 않음)"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", 
            description = "Google 인증 실패"
        )
    })
    public ApiResponse<TokenResponseDTO> googleLogin(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Google OAuth2 인증 코드",
                required = true,
                content = @Content(schema = @Schema(implementation = GoogleLoginRequestDTO.class))
            )
            @RequestBody GoogleLoginRequestDTO request) {
        
        try {
            String authorizationCode = request.getCode();
            String redirectUri = request.getRedirectUri();
            
            if (authorizationCode == null || authorizationCode.isBlank()) {
                return ApiResponse.onFailure("AUTH4001", "인증 코드가 필요합니다.", null);
            }
            
            if (redirectUri == null || redirectUri.isBlank()) {
                return ApiResponse.onFailure("AUTH4001", "리다이렉트 URI가 필요합니다.", null);
            }

            // Google OAuth2 서비스에서 사용자 정보 가져오기 및 사용자 생성/업데이트
            User user = googleOAuth2Service.authenticateWithGoogle(authorizationCode, redirectUri);

            // JWT 토큰 생성
            String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
            String refreshToken = jwtUtil.generateRefreshToken(user.getId());

            // Refresh Token을 DB에 저장
            user.updateJwtRefreshToken(refreshToken);

            // 응답 생성
            TokenResponseDTO tokenResponse = TokenResponseDTO.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(3600L) // 1시간
                    .user(UserInfoResponseDTO.from(user))
                    .build();

            log.info("Google OAuth2 로그인 성공 - 사용자 ID: {}, 이메일: {}", user.getId(), user.getEmail());

            return ApiResponse.onSuccess(tokenResponse);

        } catch (Exception e) {
            log.error("Google OAuth2 로그인 실패: {}", e.getMessage());
            return ApiResponse.onFailure("AUTH4001", "Google 로그인에 실패했습니다: " + e.getMessage(), null);
        }
    }

    @GetMapping("/url")
    @Operation(summary = "Google OAuth2 인증 URL 생성", description = "Google OAuth2 인증을 위한 URL을 생성합니다.")
    public ApiResponse<Map<String, String>> getGoogleAuthUrl() {
        // Google OAuth2 인증 URL 생성
        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=${GOOGLE_CLIENT_ID}" +
                "&redirect_uri=${GOOGLE_REDIRECT_URI}" +
                "&response_type=code" +
                "&scope=email profile" +
                "&access_type=offline" +
                "&prompt=consent";

        return ApiResponse.onSuccess(Map.of(
                "authUrl", authUrl,
                "message", "클라이언트에서 환경변수를 사용하여 실제 URL을 구성하세요."
        ));
    }
}

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/google")
@RequiredArgsConstructor
@Tag(name = "구글 로그인", description = "Google OAuth2 로그인 API입니다.")
@Slf4j
public class GoogleAuthController {

    private final GoogleOAuth2Service googleOAuth2Service;
    private final JwtUtil jwtUtil;

    @PostMapping
    @Operation(
            summary = "Google OAuth2 로그인",
            description = "프론트엔드에서 받은 Google authorization code와 redirect URI를 사용해 로그인합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = TokenResponseDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청입니다. authorization code 또는 redirect URI가 올바르지 않습니다."
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Google 인증에 실패했습니다."
            )
    })
    public ApiResponse<TokenResponseDTO> googleLogin(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Google OAuth2 로그인 요청 바디",
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
                return ApiResponse.onFailure("AUTH4001", "redirect URI가 필요합니다.", null);
            }

            User user = googleOAuth2Service.authenticateWithGoogle(authorizationCode, redirectUri);

            String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
            String refreshToken = jwtUtil.generateRefreshToken(user.getId());

            user.updateJwtRefreshToken(refreshToken);

            TokenResponseDTO tokenResponse = TokenResponseDTO.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(3600L)
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
    @Operation(summary = "Google OAuth2 URL 조회", description = "Google 로그인에 사용할 기본 인증 URL 정보를 반환합니다.")
    public ApiResponse<Map<String, String>> getGoogleAuthUrl() {
        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=${GOOGLE_CLIENT_ID}" +
                "&redirect_uri=${GOOGLE_REDIRECT_URI}" +
                "&response_type=code" +
                "&scope=email profile" +
                "&access_type=offline" +
                "&prompt=consent";

        return ApiResponse.onSuccess(Map.of(
                "authUrl", authUrl,
                "message", "클라이언트에서 환경변수를 사용해 실제 URL을 구성하세요."
        ));
    }
}

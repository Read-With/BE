package com.kw.readwith.service;

import com.kw.readwith.apiPayload.code.status.ErrorStatus;
import com.kw.readwith.apiPayload.exception.GeneralException;
import com.kw.readwith.config.JwtProperties;
import com.kw.readwith.domain.User;
import com.kw.readwith.dto.auth.TokenResponseDTO;
import com.kw.readwith.dto.auth.UserInfoResponseDTO;
import com.kw.readwith.repository.UserRepository;
import com.kw.readwith.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;

    /**
     * Refresh Token으로 Access Token 갱신
     */
    public TokenResponseDTO refreshToken(String refreshToken) {
        // Refresh Token 유효성 검증
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new GeneralException(ErrorStatus.INVALID_TOKEN);
        }

        // Refresh Token 타입 확인
        if (!"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
            throw new GeneralException(ErrorStatus.INVALID_TOKEN);
        }

        // 사용자 조회
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        // DB에 저장된 Refresh Token과 비교
        if (!refreshToken.equals(user.getJwtRefreshToken())) {
            throw new GeneralException(ErrorStatus.TOKEN_MISMATCH);
        }

        // 새로운 토큰 생성
        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());

        // 새로운 Refresh Token 저장
        user.updateJwtRefreshToken(newRefreshToken);
        userRepository.save(user);

        log.info("토큰 갱신 성공 - 사용자 ID: {}", userId);

        return TokenResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpiration())
                .user(UserInfoResponseDTO.from(user))
                .build();
    }

    /**
     * 로그아웃 (Refresh Token 무효화)
     */
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        // Refresh Token 무효화
        user.updateJwtRefreshToken(null);
        userRepository.save(user);

        log.info("로그아웃 성공 - 사용자 ID: {}", userId);
    }

    /**
     * 사용자 정보 조회
     */
    @Transactional(readOnly = true)
    public UserInfoResponseDTO getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        return UserInfoResponseDTO.from(user);
    }

    /**
     * 회원 탈퇴
     */
    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        userRepository.delete(user);

        log.info("회원 탈퇴 성공 - 사용자 ID: {}", userId);
    }
}

package com.kw.readwith.service;

import com.kw.readwith.domain.User;
import com.kw.readwith.domain.enums.Provider;
import com.kw.readwith.dto.auth.OAuth2UserInfo;
import com.kw.readwith.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GoogleOAuth2Service {

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectUri;

    /**
     * Google OAuth2 인증 코드로 사용자 정보 가져오기 및 사용자 생성/업데이트
     */
    public User authenticateWithGoogle(String authorizationCode) {
        // 1. 인증 코드로 액세스 토큰 요청
        String accessToken = getAccessToken(authorizationCode);
        
        // 2. 액세스 토큰으로 사용자 정보 요청
        OAuth2UserInfo userInfo = getUserInfo(accessToken);
        
        // 3. 사용자 정보로 DB에서 사용자 찾기 또는 생성
        return findOrCreateUser(userInfo);
    }

    private String getAccessToken(String authorizationCode) {
        String tokenUrl = "https://oauth2.googleapis.com/token";
        
        log.info("Google 액세스 토큰 요청 시작");
        log.info("Client ID: {}", clientId != null ? clientId.substring(0, Math.min(10, clientId.length())) + "..." : "null");
        log.info("Redirect URI: {}", redirectUri);
        log.info("Authorization Code: {}", authorizationCode != null ? authorizationCode.substring(0, Math.min(10, authorizationCode.length())) + "..." : "null");
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", authorizationCode);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            Map<String, Object> responseBody = response.getBody();
            
            log.info("Google 응답 상태: {}", response.getStatusCode());
            log.info("Google 응답 본문: {}", responseBody);
            
            if (responseBody != null && responseBody.containsKey("access_token")) {
                log.info("액세스 토큰 획득 성공");
                return (String) responseBody.get("access_token");
            } else {
                log.error("응답에 access_token이 없습니다. 응답: {}", responseBody);
                throw new RuntimeException("액세스 토큰을 받을 수 없습니다. 응답: " + responseBody);
            }
        } catch (Exception e) {
            log.error("Google 액세스 토큰 요청 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Google 인증에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private OAuth2UserInfo getUserInfo(String accessToken) {
        String userInfoUrl = "https://www.googleapis.com/oauth2/v2/userinfo";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(userInfoUrl, HttpMethod.GET, request, Map.class);
            Map<String, Object> userAttributes = response.getBody();
            
            if (userAttributes == null) {
                throw new RuntimeException("사용자 정보를 받을 수 없습니다.");
            }
            
            return OAuth2UserInfo.builder()
                    .providerId(String.valueOf(userAttributes.get("id")))
                    .email((String) userAttributes.get("email"))
                    .name((String) userAttributes.get("name"))
                    .profileImageUrl((String) userAttributes.get("picture"))
                    .provider("google")
                    .build();
                    
        } catch (Exception e) {
            log.error("Google 사용자 정보 요청 실패: {}", e.getMessage());
            throw new RuntimeException("Google 사용자 정보를 가져올 수 없습니다.", e);
        }
    }

    private User findOrCreateUser(OAuth2UserInfo userInfo) {
        Provider provider = Provider.GOOGLE;
        
        // 기존 사용자 조회 (providerUid로 먼저 조회)
        Optional<User> existingUser = userRepository.findByProviderAndProviderUid(
                provider, userInfo.getProviderId());
        
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            // 프로필 정보 업데이트
            user.updateProfile(userInfo.getName(), userInfo.getProfileImageUrl());
            return userRepository.save(user);
        }
        
        // 이메일로 기존 사용자 조회 (다른 소셜 로그인으로 가입한 경우)
        Optional<User> userByEmail = userRepository.findByEmail(userInfo.getEmail());
        if (userByEmail.isPresent()) {
            throw new RuntimeException("이미 다른 소셜 로그인으로 가입된 이메일입니다.");
        }
        
        // 새 사용자 생성
        User newUser = User.builder()
                .email(userInfo.getEmail())
                .nickname(userInfo.getName())
                .provider(provider)
                .providerUid(userInfo.getProviderId())
                .profileImgUrl(userInfo.getProfileImageUrl())
                .build();
        
        return userRepository.save(newUser);
    }
}

package com.kw.readwith.service;

import com.kw.readwith.domain.User;
import com.kw.readwith.domain.enums.Provider;
import com.kw.readwith.dto.auth.OAuth2UserInfo;
import com.kw.readwith.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        try {
            return processOAuth2User(userRequest, oAuth2User);
        } catch (Exception e) {
            log.error("OAuth2 사용자 처리 중 오류 발생: {}", e.getMessage());
            throw new OAuth2AuthenticationException("OAuth2 사용자 처리 실패");
        }
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo = extractUserInfo(registrationId, oAuth2User.getAttributes());
        
        if (userInfo.getEmail() == null || userInfo.getEmail().isEmpty()) {
            throw new OAuth2AuthenticationException("이메일 정보를 찾을 수 없습니다.");
        }

        User user = findOrCreateUser(userInfo);
        
        return new CustomOAuth2User(user, oAuth2User.getAttributes());
    }

    private OAuth2UserInfo extractUserInfo(String registrationId, Map<String, Object> attributes) {
        if ("google".equals(registrationId)) {
            return OAuth2UserInfo.builder()
                    .providerId(String.valueOf(attributes.get("sub")))
                    .email((String) attributes.get("email"))
                    .name((String) attributes.get("name"))
                    .profileImageUrl((String) attributes.get("picture"))
                    .provider("google")
                    .build();
        }
        
        throw new OAuth2AuthenticationException("지원하지 않는 OAuth2 제공자: " + registrationId);
    }

    private User findOrCreateUser(OAuth2UserInfo userInfo) {
        Provider provider = Provider.valueOf(userInfo.getProvider().toUpperCase());
        
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
            throw new OAuth2AuthenticationException("이미 다른 소셜 로그인으로 가입된 이메일입니다.");
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

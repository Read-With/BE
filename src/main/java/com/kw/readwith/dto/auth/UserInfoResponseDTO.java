package com.kw.readwith.dto.auth;

import com.kw.readwith.domain.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserInfoResponseDTO {
    private Long id;
    private String email;
    private String nickname;
    private String provider;
    private String profileImgUrl;
    
    public static UserInfoResponseDTO from(User user) {
        return UserInfoResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .provider(user.getProvider().name())
                .profileImgUrl(user.getProfileImgUrl())
                .build();
    }
}

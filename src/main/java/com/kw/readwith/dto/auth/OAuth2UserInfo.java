package com.kw.readwith.dto.auth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OAuth2UserInfo {
    private String providerId;
    private String email;
    private String name;
    private String profileImageUrl;
    private String provider;
}

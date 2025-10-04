package com.kw.readwith.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Google OAuth2 로그인 요청")
public class GoogleLoginRequestDTO {
    
    @Schema(description = "Google OAuth2 인증 코드", 
            example = "4/0AVGzR1C2L_Wm4VjatrKl3S2cjMUkm6gpkJ6aupz1jbDYuRDyHjtNovgPuygz5l2vFzM11A",
            required = true)
    private String code;
}

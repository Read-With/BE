package com.kw.readwith.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class SecurityUtil {

    /**
     * 현재 인증된 사용자의 ID를 가져옵니다.
     */
    public static Optional<Long> getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.empty();
            }
            
            Object principal = authentication.getPrincipal();
            if (principal instanceof Long) {
                return Optional.of((Long) principal);
            }
            
            return Optional.empty();
        } catch (Exception e) {
            log.error("현재 사용자 ID를 가져오는 중 오류 발생: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 현재 사용자가 인증되었는지 확인합니다.
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    /**
     * 현재 사용자가 특정 사용자인지 확인합니다.
     */
    public static boolean isCurrentUser(Long userId) {
        if (userId == null) {
            return false;
        }
        
        Optional<Long> currentUserId = getCurrentUserId();
        return currentUserId.isPresent() && currentUserId.get().equals(userId);
    }

    /**
     * 사용자 권한을 확인합니다.
     */
    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }
}

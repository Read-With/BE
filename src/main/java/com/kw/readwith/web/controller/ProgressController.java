package com.kw.readwith.web.controller;

import com.kw.readwith.dto.progress.ProgressResponseDTO;
import com.kw.readwith.dto.progress.SaveProgressRequestDTO;
import com.kw.readwith.service.UserReadStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final UserReadStateService userReadStateService;

    // 진도 저장/업데이트
    @PostMapping
    public ResponseEntity<ProgressResponseDTO> saveProgress(@RequestBody SaveProgressRequestDTO requestDTO) {
        // TODO: 실제 인증된 사용자 ID를 가져와야 함
        // 현재는 테스트를 위해 첫 번째 사용자 ID를 사용
        Long userId = 1L; // DataLoader에서 생성된 사용자 ID
        ProgressResponseDTO response = userReadStateService.saveProgress(userId, requestDTO);
        return ResponseEntity.ok(response);
    }

    // 특정 책의 진도 조회
    @GetMapping("/{bookId}")
    public ResponseEntity<ProgressResponseDTO> getProgress(@PathVariable Long bookId) {
        // TODO: 실제 인증된 사용자 ID를 가져와야 함
        Long userId = 1L; // DataLoader에서 생성된 사용자 ID
        ProgressResponseDTO response = userReadStateService.getProgress(userId, bookId);
        return ResponseEntity.ok(response);
    }

    // 사용자의 모든 진도 조회
    @GetMapping
    public ResponseEntity<List<ProgressResponseDTO>> getAllProgress() {
        // TODO: 실제 인증된 사용자 ID를 가져와야 함
        Long userId = 1L; // DataLoader에서 생성된 사용자 ID
        List<ProgressResponseDTO> response = userReadStateService.getAllProgress(userId);
        return ResponseEntity.ok(response);
    }

    // 진도 삭제
    @DeleteMapping("/{bookId}")
    public ResponseEntity<Void> deleteProgress(@PathVariable Long bookId) {
        // TODO: 실제 인증된 사용자 ID를 가져와야 함
        Long userId = 1L; // DataLoader에서 생성된 사용자 ID
        userReadStateService.deleteProgress(userId, bookId);
        return ResponseEntity.noContent().build();
    }
} 
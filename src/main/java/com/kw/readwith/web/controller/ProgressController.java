package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.dto.progress.ProgressResponseDTO;
import com.kw.readwith.dto.progress.SaveProgressRequestDTO;
import com.kw.readwith.service.UserReadStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
@Tag(name = "Progress", description = "독서 진도 관련 API")
public class ProgressController {

    private final UserReadStateService userReadStateService;

    // 진도 저장/업데이트
    @PostMapping
    @Operation(summary = "독서 진도 저장/업데이트", description = "사용자의 독서 진도를 저장하거나 업데이트합니다.")
    public ApiResponse<ProgressResponseDTO> saveProgress(@RequestBody SaveProgressRequestDTO requestDTO) {
        // TODO: 실제 인증된 사용자 ID를 가져와야 함
        // 현재는 테스트를 위해 첫 번째 사용자 ID를 사용
        Long userId = 1L; // DataLoader에서 생성된 사용자 ID
        ProgressResponseDTO response = userReadStateService.saveProgress(userId, requestDTO);
        return ApiResponse.onSuccess(response);
    }

    // 특정 책의 진도 조회
    @GetMapping("/{bookId}")
    @Operation(summary = "특정 책의 독서 진도 조회", description = "사용자의 특정 책에 대한 독서 진도를 조회합니다.")
    public ApiResponse<ProgressResponseDTO> getProgress(@PathVariable Long bookId) {
        // TODO: 실제 인증된 사용자 ID를 가져와야 함
        Long userId = 1L; // DataLoader에서 생성된 사용자 ID
        ProgressResponseDTO response = userReadStateService.getProgress(userId, bookId);
        return ApiResponse.onSuccess(response);
    }

    // 사용자의 모든 진도 조회
    @GetMapping
    @Operation(summary = "사용자의 모든 독서 진도 조회", description = "사용자가 읽고 있는 모든 책의 독서 진도를 조회합니다.")
    public ApiResponse<List<ProgressResponseDTO>> getAllProgress() {
        // TODO: 실제 인증된 사용자 ID를 가져와야 함
        Long userId = 1L; // DataLoader에서 생성된 사용자 ID
        List<ProgressResponseDTO> response = userReadStateService.getAllProgress(userId);
        return ApiResponse.onSuccess(response);
    }

    // 진도 삭제
    @DeleteMapping("/{bookId}")
    @Operation(summary = "독서 진도 삭제", description = "사용자의 특정 책에 대한 독서 진도를 삭제합니다.")
    public ApiResponse<Void> deleteProgress(@PathVariable Long bookId) {
        // TODO: 실제 인증된 사용자 ID를 가져와야 함
        Long userId = 1L; // DataLoader에서 생성된 사용자 ID
        userReadStateService.deleteProgress(userId, bookId);
        return ApiResponse.onSuccess(null);
    }
} 
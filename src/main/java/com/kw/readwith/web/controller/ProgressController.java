package com.kw.readwith.web.controller;

import com.kw.readwith.apiPayload.ApiResponse;
import com.kw.readwith.dto.progress.ProgressResponseDTO;
import com.kw.readwith.dto.progress.SaveProgressRequestDTO;
import com.kw.readwith.service.UserReadStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/progress", "/api/v2/progress"})
@RequiredArgsConstructor
@Tag(name = "독서 진도", description = "사용자별 현재 읽기 위치를 저장하고 조회하는 API입니다.")
public class ProgressController {

    private final UserReadStateService userReadStateService;

    @PostMapping
    @Operation(
            summary = "독서 진도 저장",
            description = "`startLocator` 기준으로 현재 읽기 위치를 저장합니다. legacy 필드 `locator`도 함께 허용합니다."
    )
    public ApiResponse<ProgressResponseDTO> saveProgress(@Valid @RequestBody SaveProgressRequestDTO requestDTO, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        ProgressResponseDTO response = userReadStateService.saveProgress(userId, requestDTO);
        return ApiResponse.onSuccess(response);
    }

    @GetMapping("/{bookId}")
    @Operation(summary = "도서별 진도 조회", description = "특정 도서에 대한 현재 사용자의 읽기 위치를 조회합니다.")
    public ApiResponse<ProgressResponseDTO> getProgress(
            @Parameter(description = "조회할 도서 ID", required = true, example = "42")
            @PathVariable Long bookId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        ProgressResponseDTO response = userReadStateService.getProgress(userId, bookId);
        return ApiResponse.onSuccess(response);
    }

    @GetMapping
    @Operation(summary = "전체 진도 조회", description = "현재 사용자가 저장한 모든 도서의 진도를 조회합니다.")
    public ApiResponse<List<ProgressResponseDTO>> getAllProgress(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<ProgressResponseDTO> response = userReadStateService.getAllProgress(userId);
        return ApiResponse.onSuccess(response);
    }

    @DeleteMapping("/{bookId}")
    @Operation(summary = "진도 삭제", description = "특정 도서에 저장된 현재 사용자의 진도를 삭제합니다.")
    public ApiResponse<Void> deleteProgress(
            @Parameter(description = "진도를 삭제할 도서 ID", required = true, example = "42")
            @PathVariable Long bookId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        userReadStateService.deleteProgress(userId, bookId);
        return ApiResponse.onSuccess(null);
    }
}

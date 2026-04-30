package com.kw.readwith.dto.admin;

import com.kw.readwith.domain.enums.ProcessingJobLogLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingJobLogResponseDTO {
    private Long id;
    private Long jobId;        // 어느 작업의 로그인지
    private String bookTitle;
    private Integer seq;
    private ProcessingJobLogLevel level;
    private String step;
    private String message;
    private String payloadJson;
    private LocalDateTime createdAt;
}

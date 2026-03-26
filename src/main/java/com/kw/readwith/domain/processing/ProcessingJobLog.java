package com.kw.readwith.domain.processing;

import com.kw.readwith.domain.common.BaseEntity;
import com.kw.readwith.domain.enums.ProcessingJobLogLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "processing_job_log", indexes = {
        @Index(name = "idx_processing_job_log_job_seq", columnList = "job_id,seq")
})
public class ProcessingJobLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;   // 로그 row 식별자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private ProcessingJob job;   // 어느 job의 로그인지 연결

    @Column(name = "seq", nullable = false)
    private Integer seq;   // job 안에서의 로그 순서

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 20)
    private ProcessingJobLogLevel level;   // INFO/ERROR 같은 로그 레벨

    @Column(name = "step", length = 80)
    private String step;   // 로그가 찍힌 처리 단계

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;   // 사람이 읽는 요약 메시지

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;   // 추가 디버깅용 구조화 데이터
}

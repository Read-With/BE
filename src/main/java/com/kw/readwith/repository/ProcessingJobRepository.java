package com.kw.readwith.repository;

import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.processing.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {

    Optional<ProcessingJob> findFirstByBookIdAndPipelineTypeAndStatusInOrderByCreatedAtDesc(
            Long bookId,
            ProcessingPipelineType pipelineType,
            Collection<ProcessingJobStatus> statuses
    );

    Optional<ProcessingJob> findTopByBookIdAndPipelineTypeOrderByCreatedAtDesc(
            Long bookId,
            ProcessingPipelineType pipelineType
    );
}

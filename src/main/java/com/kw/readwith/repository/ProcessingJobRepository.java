package com.kw.readwith.repository;

import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.processing.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import java.util.Collection;
import java.util.List;
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

    List<ProcessingJob> findAllByPipelineTypeOrderByCreatedAtDesc(
            ProcessingPipelineType pipelineType,
            Pageable pageable
    );
}

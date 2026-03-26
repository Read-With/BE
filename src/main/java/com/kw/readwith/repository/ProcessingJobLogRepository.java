package com.kw.readwith.repository;

import com.kw.readwith.domain.processing.ProcessingJobLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingJobLogRepository extends JpaRepository<ProcessingJobLog, Long> {

    long countByJobId(Long jobId);
}

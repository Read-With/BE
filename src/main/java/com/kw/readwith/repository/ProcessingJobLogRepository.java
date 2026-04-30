package com.kw.readwith.repository;

import com.kw.readwith.domain.processing.ProcessingJobLog;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ProcessingJobLogRepository extends JpaRepository<ProcessingJobLog, Long> {

    long countByJobId(Long jobId);

    List<ProcessingJobLog> findAllByJobIdOrderBySeqAsc(Long jobId);

    List<ProcessingJobLog> findAllByOrderByIdDesc(Pageable pageable);
}

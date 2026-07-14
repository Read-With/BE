package com.kw.readwith.repository;

import com.kw.readwith.domain.processing.ProcessingJob;
import com.kw.readwith.domain.processing.ProcessingJobLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import org.springframework.data.domain.Pageable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProcessingJobLogRepository extends JpaRepository<ProcessingJobLog, Long> {

    long countByJobId(Long jobId);

    List<ProcessingJobLog> findAllByJobIdOrderBySeqAsc(Long jobId);

    Optional<ProcessingJobLog> findTopByJobIdOrderBySeqDesc(Long jobId);

    List<ProcessingJobLog> findAllByOrderByIdDesc(Pageable pageable);

    @Modifying
    int deleteByJobIn(Collection<ProcessingJob> jobs);
}

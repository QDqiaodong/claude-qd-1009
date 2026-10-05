package com.karaoke.lounge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karaoke.lounge.entity.CleaningTaskLog;

public interface CleaningTaskLogRepository extends JpaRepository<CleaningTaskLog, Long> {

    List<CleaningTaskLog> findByTaskIdOrderByCreatedAtAsc(Long taskId);

    List<CleaningTaskLog> findByTaskIdInOrderByCreatedAtAsc(List<Long> taskIds);
}

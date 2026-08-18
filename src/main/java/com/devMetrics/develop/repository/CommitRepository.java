package com.devMetrics.develop.repository;

import com.devMetrics.develop.entity.Commit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommitRepository extends JpaRepository<Commit, Long> {
    boolean existsBySha(String sha);
}

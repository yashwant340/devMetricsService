package com.devMetrics.develop.repository;

import com.devMetrics.develop.entity.Commit;
import com.devMetrics.develop.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface CommitRepository extends JpaRepository<Commit, Long> {
    boolean existsBySha(String sha);
    Optional<Commit> findBySha(String sha);
    Optional<Commit> findTopByRepoOrderByCommittedAtDesc(Repository repo);

    List<Commit> findByRepo(Repository repo);
}

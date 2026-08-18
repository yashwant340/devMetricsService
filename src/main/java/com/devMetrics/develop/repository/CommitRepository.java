package com.devMetrics.develop.repository;

import com.devMetrics.develop.entity.Commit;
import com.devMetrics.develop.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommitRepository extends JpaRepository<Commit, Long> {
    boolean existsByRepoAndSha(Repository repo, String sha);

    List<Commit> findByRepo(Repository repo);

    Optional<Commit> findTopByRepoOrderByCommittedAtDesc(Repository repo);
}

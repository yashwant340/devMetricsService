package com.devMetrics.develop.repository;

import com.devMetrics.develop.entity.Commit;
import com.devMetrics.develop.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommitRepository extends JpaRepository<Commit, Long> {
    boolean existsBySha(String sha);

    List<Commit> findByRepo(Repository repo);
}

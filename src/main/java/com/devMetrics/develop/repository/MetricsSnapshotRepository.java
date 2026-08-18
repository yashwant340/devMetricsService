package com.devMetrics.develop.repository;

import com.devMetrics.develop.entity.MetricsSnapshot;
import com.devMetrics.develop.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@org.springframework.stereotype.Repository
public interface MetricsSnapshotRepository extends JpaRepository<MetricsSnapshot, UUID> {
    Optional<MetricsSnapshot> findByRepoAndSnapshotDate(Repository repo, LocalDate snapshotDate);
    Optional<MetricsSnapshot> findTopByRepoOrderBySnapshotDateDesc(Repository repo);
    List<MetricsSnapshot> findByRepoOrderBySnapshotDateAsc(Repository repo);
}

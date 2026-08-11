package com.devMetrics.develop.repository;

import com.devMetrics.develop.entity.MetricsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MetricsSnapshotRepository
        extends JpaRepository<MetricsSnapshot, UUID> {

    // Latest snapshot for a repo
    Optional<MetricsSnapshot> findTopByRepoOrderBySnapshotDateDesc(
            com.devMetrics.develop.entity.Repository repo);

    // Last N days of snapshots for trend charts
    @Query("SELECT m FROM MetricsSnapshot m " +
            "WHERE m.repo = :repo " +
            "AND m.snapshotDate >= :since " +
            "ORDER BY m.snapshotDate ASC")
    List<MetricsSnapshot> findByRepoSince(
            @Param("repo") com.devMetrics.develop.entity.Repository repo,
            @Param("since") LocalDate since);

    // Check if today's snapshot already exists
    boolean existsByRepoAndSnapshotDate(
            Repository repo, LocalDate snapshotDate);

    // All snapshots for a repo ordered by date
    @Query("SELECT m FROM MetricsSnapshot m " +
            "WHERE m.repo = :repo " +
            "ORDER BY m.snapshotDate DESC")
    List<MetricsSnapshot> findAllByRepoOrderByDateDesc(
            @Param("repo") com.devMetrics.develop.entity.Repository repo);

    // Add this method — needed by computeAndSnapshot upsert logic
    @Query("SELECT m FROM MetricsSnapshot m " +
            "WHERE m.repo = :repo " +
            "AND m.snapshotDate = :date")
    Optional<MetricsSnapshot> findByRepoAndSnapshotDate(
            @Param("repo") com.devMetrics.develop.entity.Repository repo,
            @Param("date") LocalDate date);

}
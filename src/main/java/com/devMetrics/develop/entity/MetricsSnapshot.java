package com.devMetrics.develop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "metrics_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private Repository repo;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "avg_pr_merge_hours")
    private Double avgPrMergeHours;

    @Column(name = "avg_time_to_first_review_hours")
    private Double avgTimeToFirstReviewHours;

    @Column(name = "open_pr_count")
    private Integer openPrCount;

    @Column(name = "merged_pr_count")
    private Integer mergedPrCount;

    @Column(name = "closed_pr_count")
    private Integer closedPrCount;

    @Column(name = "total_lines_added")
    private Integer totalLinesAdded;

    @Column(name = "total_lines_deleted")
    private Integer totalLinesDeleted;

    @Column(name = "total_commits")
    private Integer totalCommits;

    @Column(name = "churn_ratio")
    private Double churnRatio;

    @Column(name = "active_contributors")
    private Integer activeContributors;

    @Column(name = "avg_prs_per_contributor_per_week")
    private Double avgPrsPerContributorPerWeek;

    @Column(name = "avg_commits_per_contributor_per_week")
    private Double avgCommitsPerContributorPerWeek;

    @Column(name = "health_score")
    private Integer healthScore;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}

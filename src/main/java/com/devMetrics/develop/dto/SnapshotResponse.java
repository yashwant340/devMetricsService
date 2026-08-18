package com.devMetrics.develop.dto;

import com.devMetrics.develop.entity.MetricsSnapshot;

import java.time.LocalDate;
import java.util.UUID;

public record SnapshotResponse(
        UUID id,
        LocalDate snapshotDate,

        // PR metrics
        Double      avgPrMergeHours,
        Double      avgTimeToFirstReviewHours,
        Integer     openPrCount,
        Integer     mergedPrCount,
        Integer     closedPrCount,

        // Churn
        Integer     totalLinesAdded,
        Integer     totalLinesDeleted,
        Integer     totalCommits,
        Double      churnRatio,

        // Velocity
        Integer     activeContributors,
        Double      avgPrsPerContributorPerWeek,
        Double      avgCommitsPerContributorPerWeek,

        // Health
        Integer     healthScore
) {
    public static SnapshotResponse from(MetricsSnapshot m) {
        return new SnapshotResponse(
                m.getId(),
                m.getSnapshotDate(),
                m.getAvgPrMergeHours(),
                m.getAvgTimeToFirstReviewHours(),
                m.getOpenPrCount(),
                m.getMergedPrCount(),
                m.getClosedPrCount(),
                m.getTotalLinesAdded(),
                m.getTotalLinesDeleted(),
                m.getTotalCommits(),
                m.getChurnRatio(),
                m.getActiveContributors(),
                m.getAvgPrsPerContributorPerWeek(),
                m.getAvgCommitsPerContributorPerWeek(),
                m.getHealthScore()
        );
    }
}

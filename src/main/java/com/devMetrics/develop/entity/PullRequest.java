package com.devMetrics.develop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pull_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private Repository repo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Contributor author;

    @Column(name = "github_pr_number", nullable = false)
    private Integer githubPrNumber;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "state", nullable = false)
    private String state;               // open, closed, merged

    @Column(name = "additions")
    private Integer additions;

    @Column(name = "deletions")
    private Integer deletions;

    @Column(name = "changed_files")
    private Integer changedFiles;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "first_review_at")
    private Instant firstReviewAt;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }
}

package com.devMetrics.develop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pr_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    private PullRequest pullRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private Contributor reviewer;

    @Column(name = "github_review_id", unique = true)
    private Long githubReviewId;

    @Column(name = "state")
    private String state;           // APPROVED, CHANGES_REQUESTED, COMMENTED

    @Column(name = "submitted_at")
    private Instant submittedAt;
}

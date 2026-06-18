package com.devMetrics.develop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contributors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contributor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private Repository repo;

    @Column(name = "github_login", nullable = false)
    private String githubLogin;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @PrePersist
    void onCreate() { firstSeenAt = Instant.now(); }
}
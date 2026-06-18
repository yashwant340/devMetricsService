package com.devMetrics.develop.repository;

import com.devMetrics.develop.entity.PullRequest;
import com.devMetrics.develop.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@org.springframework.stereotype.Repository
public interface PullRequestRepository
        extends JpaRepository<PullRequest, UUID> {

    @Query("SELECT DISTINCT p FROM PullRequest p " +
            "WHERE p.repo = :repo " +
            "AND p.githubPrNumber = :prNumber")
    Optional<PullRequest> findByRepoAndGithubPrNumber(
            @Param("repo") Repository repo,
            @Param("prNumber") Integer prNumber);

    @Query("SELECT DISTINCT p FROM PullRequest p WHERE p.repo = :repo")
    List<PullRequest> findByRepo(@Param("repo") Repository repo);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
            "FROM PullRequest p " +
            "WHERE p.repo = :repo " +
            "AND p.githubPrNumber = :prNumber")
    boolean existsByRepoAndGithubPrNumber(
            @Param("repo") Repository repo,
            @Param("prNumber") Integer prNumber);
}
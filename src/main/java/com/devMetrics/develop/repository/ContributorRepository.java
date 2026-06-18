package com.devMetrics.develop.repository;

import com.devMetrics.develop.entity.Contributor;
import com.devMetrics.develop.entity.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

@org.springframework.stereotype.Repository
public interface ContributorRepository
        extends JpaRepository<Contributor, UUID> {

    @Query("SELECT DISTINCT c FROM Contributor c " +
            "WHERE c.repo = :repo " +
            "AND c.githubLogin = :githubLogin")
    Optional<Contributor> findByRepoAndGithubLogin(
            @Param("repo") Repository repo,
            @Param("githubLogin") String githubLogin);
}
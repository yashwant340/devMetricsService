package com.devMetrics.develop.repository;

import com.devMetrics.develop.entity.Repository;
import com.devMetrics.develop.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@org.springframework.stereotype.Repository
public interface RepositoryRepository extends JpaRepository<Repository, UUID> {

    List<Repository> findByOwner(User user);

    boolean existsByGithubRepoId(Long id);
}

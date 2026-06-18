package com.devMetrics.develop.repository;

import com.devMetrics.develop.entity.PrReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrReviewRepository extends JpaRepository<PrReview, Long> {
    boolean existsByGithubReviewId(Long id);
}

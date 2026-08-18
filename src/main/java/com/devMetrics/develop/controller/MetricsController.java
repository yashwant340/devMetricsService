package com.devMetrics.develop.controller;

import com.devMetrics.develop.entity.MetricsSnapshot;
import com.devMetrics.develop.entity.Repository;
import com.devMetrics.develop.entity.User;
import com.devMetrics.develop.exceptions.RepoNotFoundException;
import com.devMetrics.develop.repository.MetricsSnapshotRepository;
import com.devMetrics.develop.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final RepositoryRepository repositoryRepository;
    private final MetricsSnapshotRepository metricsSnapshotRepository;

    @GetMapping("/{repoId}/latest")
    public ResponseEntity<?> latest(
            @PathVariable UUID repoId,
            Authentication auth) {
        User user = (User) auth.getPrincipal();
        Repository repo = findOwnedRepo(repoId, user);

        return metricsSnapshotRepository.findTopByRepoOrderBySnapshotDateDesc(repo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/{repoId}/history")
    public ResponseEntity<List<MetricsSnapshot>> history(
            @PathVariable UUID repoId,
            @RequestParam(defaultValue = "30") int days,
            Authentication auth) {
        User user = (User) auth.getPrincipal();
        Repository repo = findOwnedRepo(repoId, user);

        LocalDate cutoff = LocalDate.now().minusDays(days);
        List<MetricsSnapshot> snapshots = metricsSnapshotRepository
                .findByRepoOrderBySnapshotDateAsc(repo)
                .stream()
                .filter(snapshot -> snapshot.getSnapshotDate() != null
                        && !snapshot.getSnapshotDate().isBefore(cutoff))
                .toList();

        return ResponseEntity.ok(snapshots);
    }

    private Repository findOwnedRepo(UUID repoId, User user) {
        Repository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new RepoNotFoundException("Repo not found"));
        if (!repo.getOwner().getId().equals(user.getId())) {
            throw new SecurityException("Not your repo");
        }
        return repo;
    }
}

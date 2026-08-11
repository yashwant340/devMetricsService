package com.devMetrics.develop.controller;

import com.devMetrics.develop.dto.SnapshotResponse;
import com.devMetrics.develop.entity.Repository;
import com.devMetrics.develop.entity.User;
import com.devMetrics.develop.exceptions.RepoNotFoundException;
import com.devMetrics.develop.repository.MetricsSnapshotRepository;
import com.devMetrics.develop.repository.RepositoryRepository;
import com.devMetrics.develop.service.MetricsComputationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsSnapshotRepository metricsSnapshotRepository;
    private final RepositoryRepository repositoryRepository;
    private final MetricsComputationService metricsComputationService;

    // GET /api/metrics/{repoId}/latest
    // Returns the most recent snapshot for a repo
    @GetMapping("/{repoId}/latest")
    public ResponseEntity<?> getLatest(
            @PathVariable UUID repoId,
            Authentication auth) {

        User user = (User) auth.getPrincipal();
        Repository repo = resolveRepo(repoId, user);

        return metricsSnapshotRepository
                .findTopByRepoOrderBySnapshotDateDesc(repo)
                .map(s -> ResponseEntity.ok(SnapshotResponse.from(s)))
                .orElse(ResponseEntity.noContent().build());
    }

    // GET /api/metrics/{repoId}/history?days=30
    // Returns trend data for charts — defaults to last 30 days
    @GetMapping("/{repoId}/history")
    public ResponseEntity<?> getHistory(
            @PathVariable UUID repoId,
            @RequestParam(defaultValue = "30") int days,
            Authentication auth) {

        User user = (User) auth.getPrincipal();
        Repository repo = resolveRepo(repoId, user);

        LocalDate since = LocalDate.now().minusDays(days);

        List<SnapshotResponse> history = metricsSnapshotRepository
                .findByRepoSince(repo, since)
                .stream()
                .map(SnapshotResponse::from)
                .toList();

        return ResponseEntity.ok(history);
    }

    // POST /api/metrics/{repoId}/compute
    // Manually trigger recomputation without a full sync
    @PostMapping("/{repoId}/compute")
    public ResponseEntity<?> recompute(
            @PathVariable UUID repoId,
            Authentication auth) {

        User user = (User) auth.getPrincipal();
        Repository repo = resolveRepo(repoId, user);

        metricsComputationService.computeAndSnapshot(repo);

        return metricsSnapshotRepository
                .findTopByRepoOrderBySnapshotDateDesc(repo)
                .map(s -> ResponseEntity.ok(SnapshotResponse.from(s)))
                .orElse(ResponseEntity.noContent().build());
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private Repository resolveRepo(UUID repoId, User user) {
        Repository repo = repositoryRepository.findById(repoId)
                .filter(Repository::isConnected)
                .orElseThrow(() ->
                        new RepoNotFoundException("Repo not found"));

        if (!repo.getOwner().getId().equals(user.getId())) {
            throw new SecurityException("Not your repo");
        }

        return repo;
    }

    // ── Response DTO ─────────────────────────────────────────────────────


}

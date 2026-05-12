package com.devMetrics.develop.service;

import com.devMetrics.develop.entity.Repository;
import com.devMetrics.develop.entity.User;
import com.devMetrics.develop.exceptions.RepoAlreadyConnectedException;
import com.devMetrics.develop.exceptions.RepoNotFoundException;
import com.devMetrics.develop.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryService {

    private final RepositoryRepository repositoryRepository;
    private final GitHubApiService gitHubApiService;

    // Return all repos already connected by this user
    public List<Repository> getConnectedRepos(User user) {
        return repositoryRepository.findByOwner(user);
    }

    // Fetch all repos visible to the user from GitHub (for the picker UI)
    public List<GitHubApiService.GitHubRepoDto> getAvailableRepos(User user) {
        return gitHubApiService.fetchUserRepos(user.getAccessToken());
    }

    // Connect a repo: validate it on GitHub, then save it
    public Repository connectRepo(User user, String fullName) {

        // Check not already connected by this user
        GitHubApiService.GitHubRepoDto ghRepo =
                gitHubApiService.fetchRepo(user.getAccessToken(), fullName);

        if (repositoryRepository.existsByGithubRepoId(ghRepo.id())) {
            throw new RepoAlreadyConnectedException(
                    fullName + " is already connected");
        }

        Repository repo = Repository.builder()
                .owner(user)
                .githubRepoId(ghRepo.id())
                .fullName(ghRepo.fullName())
                .name(ghRepo.name())
                .ownerLogin(ghRepo.owner().login())
                .description(ghRepo.description())
                .defaultBranch(ghRepo.defaultBranch())
                .isPrivate(ghRepo.isPrivate())
                .starsCount(ghRepo.starsCount())
                .language(ghRepo.language())
                .build();

        Repository saved = repositoryRepository.save(repo);
        log.info("Repo connected: {} by user: {}", fullName, user.getLogin());
        return saved;
    }

    // Disconnect (delete) a repo
    public void disconnectRepo(User user, UUID repoId) {
        Repository repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new RepoNotFoundException(
                        "Repo not found: " + repoId));

        if (!repo.getOwner().getId().equals(user.getId())) {
            throw new SecurityException("Not your repo");
        }

        repositoryRepository.delete(repo);
        log.info("Repo disconnected: {} by user: {}",
                repo.getFullName(), user.getLogin());
    }
}

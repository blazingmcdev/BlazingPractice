package net.blazingpractice.update;

import net.blazingpractice.BlazingPractice;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GitHub Releases checker. It reports updates but never replaces plugin JAR files. */
public final class UpdateChecker {
    private static final Pattern TAG_PATTERN = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final BlazingPractice plugin;
    private final HttpClient client;
    private volatile String latestVersion;

    public UpdateChecker(BlazingPractice plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("update-checker.enabled", false)) return;
        if (plugin.getConfig().getBoolean("update-checker.check-on-startup", true)) {
            checkNow().thenAccept(result -> {
                if (result.success() && result.version() != null
                        && !normalize(result.version()).equals(normalize(plugin.getPluginMeta().getVersion()))) {
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::notifyIfOutdated));
                }
            });
        }
    }

    public CompletableFuture<UpdateResult> checkNow() {
        String repository = plugin.getConfig().getString("update-checker.github-repository", "").trim();
        if (repository.isEmpty() || repository.contains("YOUR_GITHUB_USERNAME")) {
            return CompletableFuture.completedFuture(new UpdateResult(false, null,
                    "§eSet update-checker.github-repository to owner/repository in config.yml first."));
        }
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            return CompletableFuture.completedFuture(new UpdateResult(false, null,
                    "§cInvalid GitHub repository setting. Expected owner/repository."));
        }

        URI uri = URI.create("https://api.github.com/repos/" + repository + "/releases/latest");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "BlazingPractice-UpdateChecker")
                .GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 404) return new UpdateResult(false, null,
                            "§eNo published GitHub Release was found for " + repository + ".");
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return new UpdateResult(false, null, "§cGitHub update check failed (HTTP " + response.statusCode() + ").");
                    }
                    Matcher matcher = TAG_PATTERN.matcher(response.body());
                    if (!matcher.find()) return new UpdateResult(false, null, "§cCould not read tag_name from the GitHub Release response.");
                    String tag = matcher.group(1);
                    latestVersion = tag;
                    String current = plugin.getPluginMeta().getVersion();
                    if (normalize(tag).equals(normalize(current))) {
                        return new UpdateResult(true, tag, "§aBlazingPractice is up to date (" + tag + ").");
                    }
                    return new UpdateResult(true, tag, "§eA release is available: " + tag + " (installed: " + current + "). Download it from GitHub Releases; no automatic JAR replacement is performed.");
                })
                .exceptionally(error -> new UpdateResult(false, null,
                        "§cGitHub update check failed: " + Optional.ofNullable(error.getMessage()).orElse("network error")));
    }

    public void notifyIfOutdated(Player player) {
        if (!plugin.getConfig().getBoolean("update-checker.enabled", false)
                || !plugin.getConfig().getBoolean("update-checker.notify-admins-on-join", true)
                || !player.hasPermission("blazingpractice.admin.updates")) return;
        String latest = latestVersion;
        if (latest == null) return;
        String current = plugin.getPluginMeta().getVersion();
        if (!normalize(latest).equals(normalize(current))) {
            player.sendMessage("§e[BlazingPractice] New release " + latest + " is available. Installed: " + current + ".");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceFirst("^[vV]", "");
    }

    public record UpdateResult(boolean success, String version, String message) { }
}

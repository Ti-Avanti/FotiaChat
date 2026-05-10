package gg.fotia.chat.update;

import gg.fotia.chat.FotiaChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {

    private static final String GITHUB_OWNER = "Ti-Avanti";
    private static final String GITHUB_REPO = "FotiaChat";
    private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/"
            + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    private static final String UPDATE_NOTIFY_PERMISSION = "fotiachat.update.notify";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HTML_URL_PATTERN = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private final FotiaChat plugin;
    private final HttpClient httpClient;
    private volatile UpdateInfo availableUpdate;

    public UpdateChecker(FotiaChat plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    public void checkOnStartup() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UpdateCheckResult result = checkLatestRelease();
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> handleStartupResult(result));
        });
    }

    public void notifyPlayerIfUpdateAvailable(Player player) {
        if (player == null || !player.isOnline() || !player.hasPermission(UPDATE_NOTIFY_PERMISSION)) {
            return;
        }

        UpdateInfo updateInfo = availableUpdate;
        if (updateInfo == null) {
            return;
        }

        player.sendMessage(plugin.getMessageManager().get("update.available", Map.of(
                "current", updateInfo.currentVersion(),
                "latest", updateInfo.latestVersion()
        )));
        player.sendMessage(buildReleaseLinkMessage(player, updateInfo.releaseUrl()));
    }

    private UpdateCheckResult checkLatestRelease() {
        String currentVersion = plugin.getDescription().getVersion();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LATEST_RELEASE_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "FotiaChat/" + currentVersion)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return UpdateCheckResult.failed("GitHub Release 不存在");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return UpdateCheckResult.failed("GitHub API 返回 HTTP " + response.statusCode());
            }

            String latestTag = extractJsonString(response.body(), TAG_NAME_PATTERN);
            if (latestTag.isEmpty()) {
                return UpdateCheckResult.failed("GitHub Release 缺少 tag_name");
            }

            String latestVersion = normalizeVersion(latestTag);
            if (latestVersion.isEmpty()) {
                return UpdateCheckResult.failed("无法解析 GitHub 版本号: " + latestTag);
            }

            String releaseUrl = extractJsonString(response.body(), HTML_URL_PATTERN);
            if (releaseUrl.isEmpty()) {
                releaseUrl = "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
            }

            if (compareVersions(latestVersion, currentVersion) > 0) {
                return UpdateCheckResult.updateAvailable(new UpdateInfo(currentVersion, latestVersion, releaseUrl));
            }
            return UpdateCheckResult.upToDate(currentVersion);
        } catch (IOException exception) {
            return UpdateCheckResult.failed(exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return UpdateCheckResult.failed("更新检测线程被中断");
        } catch (Exception exception) {
            return UpdateCheckResult.failed(exception.getMessage());
        }
    }

    private void handleStartupResult(UpdateCheckResult result) {
        if (result.updateInfo() != null) {
            availableUpdate = result.updateInfo();
            plugin.getLogger().warning(plugin.getMessageManager().getRaw("update.console-available", Map.of(
                    "current", result.updateInfo().currentVersion(),
                    "latest", result.updateInfo().latestVersion(),
                    "url", result.updateInfo().releaseUrl()
            )));
            notifyOnlineAdmins();
            return;
        }

        if (result.upToDateVersion() != null) {
            plugin.getLogger().info(plugin.getMessageManager().getRaw("update.console-latest",
                    Map.of("current", result.upToDateVersion())));
            return;
        }

        plugin.getLogger().warning(plugin.getMessageManager().getRaw("update.console-failed",
                Map.of("reason", result.failureReason())));
    }

    private void notifyOnlineAdmins() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            notifyPlayerIfUpdateAvailable(player);
        }
    }

    private Component buildReleaseLinkMessage(Player player, String releaseUrl) {
        Component component = plugin.getMessageManager().get("update.release-url", player, Map.of("url", releaseUrl));
        return component
                .clickEvent(ClickEvent.openUrl(releaseUrl))
                .hoverEvent(HoverEvent.showText(plugin.getMessageManager().get("update.release-hover", player)));
    }

    private String extractJsonString(String json, Pattern pattern) {
        if (json == null || json.isEmpty()) {
            return "";
        }

        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return "";
        }
        return unescapeJsonString(matcher.group(1));
    }

    private String unescapeJsonString(String value) {
        return value.replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private int compareVersions(String latestVersion, String currentVersion) {
        List<Integer> latestParts = parseVersionNumbers(latestVersion);
        List<Integer> currentParts = parseVersionNumbers(currentVersion);
        int maxLength = Math.max(latestParts.size(), currentParts.size());

        for (int index = 0; index < maxLength; index++) {
            int latest = index < latestParts.size() ? latestParts.get(index) : 0;
            int current = index < currentParts.size() ? currentParts.get(index) : 0;
            int comparison = Integer.compare(latest, current);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private List<Integer> parseVersionNumbers(String version) {
        List<Integer> numbers = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(version == null ? "" : version);
        while (matcher.find()) {
            try {
                numbers.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
                numbers.add(0);
            }
        }
        if (numbers.isEmpty()) {
            numbers.add(0);
        }
        return numbers;
    }

    private record UpdateInfo(String currentVersion, String latestVersion, String releaseUrl) {
    }

    private record UpdateCheckResult(UpdateInfo updateInfo, String upToDateVersion, String failureReason) {

        private static UpdateCheckResult updateAvailable(UpdateInfo updateInfo) {
            return new UpdateCheckResult(updateInfo, null, null);
        }

        private static UpdateCheckResult upToDate(String version) {
            return new UpdateCheckResult(null, version, null);
        }

        private static UpdateCheckResult failed(String reason) {
            String safeReason = reason == null || reason.isBlank() ? "未知错误" : reason;
            return new UpdateCheckResult(null, null, safeReason);
        }
    }
}

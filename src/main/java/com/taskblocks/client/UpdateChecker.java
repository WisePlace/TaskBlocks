package com.taskblocks.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taskblocks.TaskBlocks;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

// Checks the GitHub repo's latest release against the currently
// installed version once per game session, notifying in chat if a
// newer version is available.
public class UpdateChecker {

    private static final String REPO = "WisePlace/TaskBlocks";
    private static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String RELEASE_URL = "https://github.com/" + REPO + "/releases/latest";
    private static final String MOD_ID = "taskblocks";

    private static boolean checkedThisSession = false;

    public static void checkOnce() {
        if (checkedThisSession) return;
        checkedThisSession = true;

        Thread thread = new Thread(UpdateChecker::checkNow, "TaskBlocks-UpdateCheck");
        thread.setDaemon(true);
        thread.start();
    }

    private static void checkNow() {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!json.has("tag_name")) return;

            String latestTag = json.get("tag_name").getAsString();
            String latestVersion = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;
            String currentVersion = getCurrentVersion();

            TaskBlocks.LOGGER.info("[TaskBlocks] Update check: current=" + currentVersion
                + ", latest=" + latestVersion);

            if (VersionUtil.isNewer(latestVersion, currentVersion)) {
                notifyUpdate(currentVersion, latestVersion);
            }
        } catch (Exception e) {
            TaskBlocks.LOGGER.info("[TaskBlocks] Update check failed: " + e.getMessage());
        }
    }

    private static void notifyUpdate(String currentVersion, String latestVersion) {
        MinecraftClient client = MinecraftClient.getInstance();

        Text header = Text.literal("\u2728 TaskBlocks Update Available \u2728")
            .styled(style -> style.withColor(0xFFD700).withBold(true));

        Text versionLine = Text.literal("You have " + currentVersion + "   \u2192   Latest is " + latestVersion)
            .styled(style -> style.withColor(0xAAAAAA));

        Text downloadLink = Text.literal("\u25B6 Download latest version")
            .styled(style -> style
                .withColor(0x55FFFF)
                .withBold(true)
                .withUnderline(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(RELEASE_URL)))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal(RELEASE_URL))));

        client.execute(() -> {
            if (client.player == null) return;
            client.player.sendMessage(Text.literal(""), false);
            client.player.sendMessage(header, false);
            client.player.sendMessage(versionLine, false);
            client.player.sendMessage(downloadLink, false);
            client.player.sendMessage(Text.literal(""), false);
        });
    }

    private static String getCurrentVersion() {
        return FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("0.0.0");
    }
}
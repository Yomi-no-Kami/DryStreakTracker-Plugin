package com.harrystyles.drystreaktracker.wiki;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Loads NPC drop information from the OSRS Wiki Bucket API.
 *
 * No HTML scraping is performed.
 *
 * NPC drop tables are cached for the lifetime of the plugin
 * so repeated configuration does not repeatedly contact the Wiki.
 */
@Slf4j
@Singleton
public class WikiDropService {
    private static final String WIKI_API_URL = "https://oldschool.runescape.wiki/api.php";

    private static final int MAX_WIKI_DROPS = 500;

    private final OkHttpClient okHttpClient;
    private final Gson gson;

    private final Map<Integer, List<WikiDrop>> npcDropCache = new ConcurrentHashMap<>();
    private final Map<Integer, String> npcImageFileNameCache = new ConcurrentHashMap<>();

    @Inject
    public WikiDropService(OkHttpClient okHttpClient, Gson gson) {
        this.okHttpClient = okHttpClient;
        this.gson = gson;
    }

    /**
     * Gets the complete selectable Wiki drop table for an NPC.
     *
     * The Wiki is contacted only when this NPC has not already
     * been cached during the current plugin session.
     */
    public CompletableFuture<List<WikiDrop>> getDrops(String npcName, int npcId) {
        List<WikiDrop> cachedDrops = npcDropCache.get(npcId);

        if (cachedDrops != null) {
            return CompletableFuture.completedFuture(new ArrayList<>(cachedDrops));
        }

        CompletableFuture<List<WikiDrop>> future = new CompletableFuture<>();

        lookupNpcPage(npcName, npcId).whenComplete((pageName, lookupError) -> {
            if (lookupError != null) {
                future.completeExceptionally(lookupError);

                return;
            }

            if (pageName == null || pageName.trim().isEmpty()) {
                future.complete(Collections.emptyList());

                return;
            }

            loadNpcImageFileName(pageName).whenComplete((imageFileName, imageError) -> {
                if (imageError != null) {
                    log.debug("Could not load Wiki image for NPC {} ({})", npcName, npcId, imageError);
                } else if (imageFileName != null && !imageFileName.trim().isEmpty()) {
                    npcImageFileNameCache.put(npcId, imageFileName);
                }

                loadDropsForPage(pageName).whenComplete((drops, dropError) -> {
                    if (dropError != null) {
                        future.completeExceptionally(dropError);

                        return;
                    }

                    List<WikiDrop> safeDrops = drops == null
                            ? Collections.emptyList()
                            : new ArrayList<>(drops);

                    npcDropCache.put(npcId, Collections.unmodifiableList(new ArrayList<>(safeDrops)));

                    future.complete(safeDrops);
                });
            });
        });

        return future;
    }

    public String getCachedNpcImageFileName(int npcId) {
        return npcImageFileNameCache.get(npcId);
    }

    public void clearCache() {
        npcDropCache.clear();
        npcImageFileNameCache.clear();
    }

    /**
     * Resolves an in-game NPC ID to the correct OSRS Wiki page.
     */
    private CompletableFuture<String> lookupNpcPage(String npcName, int npcId) {
        String query = "bucket('npc_id')"
                + ".select('page_name','id')"
                + ".where('id'," + npcId + ")"
                + ".limit(10)"
                + ".run()";

        CompletableFuture<String> future = new CompletableFuture<>();

        executeBucketQuery(query).whenComplete((response, error) -> {
            if (error != null) {
                future.completeExceptionally(error);

                return;
            }

            JsonArray results = getBucketResults(response);

            if (results == null || results.size() == 0) {
                log.warn("OSRS Wiki could not resolve NPC {} ({})", npcName, npcId);

                future.complete(null);

                return;
            }

            /*
             * One NPC ID should normally resolve to exactly one
             * page. Use the first valid result.
             */
            for (JsonElement element : results) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }

                JsonObject result = element.getAsJsonObject();

                String pageName = getString(result, "page_name");

                if (pageName != null && !pageName.trim().isEmpty()) {
                    log.debug("Resolved NPC {} ({}) to Wiki page '{}'", npcName, npcId, pageName);

                    future.complete(pageName.trim());

                    return;
                }
            }

            future.complete(null);
        });

        return future;
    }

    /**
     * Loads item names and item IDs for every drop belonging
     * to one Wiki NPC page.
     *
     * dropsline supplies the drop rows.
     * infobox_item supplies the actual in-game item ID.
     */
    private CompletableFuture<List<WikiDrop>> loadDropsForPage(String pageName) {
        String escapedPageName = escapeBucketString(pageName);

        String query = "bucket('dropsline')"
                + ".join('infobox_item','dropsline.item_name','infobox_item.item_name')"
                + ".select('page_name','item_name','infobox_item.item_id','infobox_item.version_anchor','infobox_item.default_version')"
                + ".where('page_name','" + escapedPageName + "')"
                + ".limit(" + MAX_WIKI_DROPS + ")"
                + ".run()";

        CompletableFuture<List<WikiDrop>> future = new CompletableFuture<>();

        executeBucketQuery(query).whenComplete((response, error) -> {
            if (error != null) {
                future.completeExceptionally(error);

                return;
            }

            JsonArray results = getBucketResults(response);

            if (results == null || results.size() == 0) {
                future.complete(Collections.emptyList());

                return;
            }

            /*
             * Multiple drop-table rows can refer to the same item
             * at different quantities or rarities.
             *
             * The tracker only needs the item once.
             */
            Map<String, WikiDrop> uniqueDrops = new LinkedHashMap<>();
            Map<String, Boolean> defaultVersionByName = new HashMap<>();

            for (JsonElement element : results) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }

                JsonObject result = element.getAsJsonObject();

                String itemName = getString(result, "item_name");

                if (itemName == null || itemName.trim().isEmpty()) {
                    continue;
                }

                if (itemName.equalsIgnoreCase("Nothing")) {
                    continue;
                }

                int itemId = getItemId(result);

                if (itemId <= 0) {
                    log.debug("Could not resolve Wiki item ID for '{}'", itemName);

                    continue;
                }

                String normalizedItemName = itemName.trim().toLowerCase();
                boolean defaultVersion = getBoolean(result, "infobox_item.default_version");

                WikiDrop existingDrop = uniqueDrops.get(normalizedItemName);
                boolean existingIsDefault = defaultVersionByName.getOrDefault(normalizedItemName, false);

                /*
                 * Prefer the Wiki's explicitly marked default item version.
                 *
                 * If no version has been stored yet, accept this row.
                 * If we already have a non-default row and this row is
                 * the default version, replace it.
                 */
                if (existingDrop == null || (!existingIsDefault && defaultVersion)) {
                    uniqueDrops.put(normalizedItemName, new WikiDrop(itemId, itemName.trim()));
                    defaultVersionByName.put(normalizedItemName, defaultVersion);
                }
            }

            List<WikiDrop> drops = new ArrayList<>(uniqueDrops.values());

            drops.sort(Comparator.comparing(
                    WikiDrop::getItemName,
                    String.CASE_INSENSITIVE_ORDER
            ));

            log.debug("Loaded {} selectable Wiki drops for page '{}'", drops.size(), pageName);

            future.complete(drops);
        });

        return future;
    }

    /**
     * Loads the representative image filename for an NPC's
     * OSRS Wiki page.
     *
     * The returned value is only the Wiki filename, such as:
     *
     * Man.png
     *
     * EncounterDefinition will turn that into the full Wiki
     * image URL when the encounter is displayed.
     */
    private CompletableFuture<String> loadNpcImageFileName(String pageName) {
        CompletableFuture<String> future = new CompletableFuture<>();

        HttpUrl baseUrl = HttpUrl.parse(WIKI_API_URL);

        if (baseUrl == null) {
            future.complete(null);

            return future;
        }

        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("format", "json")
                .addQueryParameter("formatversion", "2")
                .addQueryParameter("prop", "pageimages")
                .addQueryParameter("piprop", "name")
                .addQueryParameter("titles", pageName)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "DryStreakTracker RuneLite Plugin")
                .header("Accept", "application/json")
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        future.complete(null);

                        return;
                    }

                    JsonObject json;

                    try {
                        json = gson.fromJson(body.charStream(), JsonObject.class);
                    } catch (Exception e) {
                        future.completeExceptionally(e);

                        return;
                    }

                    if (json == null || !json.has("query") || !json.get("query").isJsonObject()) {
                        future.complete(null);

                        return;
                    }

                    JsonObject query = json.getAsJsonObject("query");

                    if (!query.has("pages") || !query.get("pages").isJsonArray()) {
                        future.complete(null);

                        return;
                    }

                    JsonArray pages = query.getAsJsonArray("pages");

                    for (JsonElement pageElement : pages) {
                        if (pageElement == null || !pageElement.isJsonObject()) {
                            continue;
                        }

                        JsonObject page = pageElement.getAsJsonObject();

                        String imageFileName = getString(page, "pageimage");

                        if (imageFileName == null || imageFileName.trim().isEmpty()) {
                            continue;
                        }

                        imageFileName = imageFileName.trim().replace(" ", "_");

                        /*
                         * EncounterDefinition intentionally only accepts
                         * a Wiki filename rather than an arbitrary URL.
                         */
                        if (imageFileName.contains("/")
                                || imageFileName.contains("\\")
                                || imageFileName.contains(":")) {
                            continue;
                        }

                        log.debug(
                                "Resolved Wiki image '{}' for page '{}'",
                                imageFileName,
                                pageName
                        );

                        future.complete(imageFileName);

                        return;
                    }

                    future.complete(null);
                }
            }
        });

        return future;
    }

    private CompletableFuture<JsonObject> executeBucketQuery(String query) {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        HttpUrl baseUrl = HttpUrl.parse(WIKI_API_URL);

        if (baseUrl == null) {
            future.completeExceptionally(new IllegalStateException("Invalid OSRS Wiki API URL"));

            return future;
        }

        HttpUrl url = baseUrl.newBuilder()
                .addQueryParameter("action", "bucket")
                .addQueryParameter("format", "json")
                .addQueryParameter("formatversion", "2")
                .addQueryParameter("query", query)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "DryStreakTracker RuneLite Plugin")
                .header("Accept", "application/json")
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        future.completeExceptionally(
                                new IOException("OSRS Wiki returned HTTP " + response.code())
                        );

                        return;
                    }

                    JsonObject json;

                    try {
                        json = gson.fromJson(body.charStream(), JsonObject.class);
                    } catch (Exception e) {
                        future.completeExceptionally(e);

                        return;
                    }

                    if (json == null) {
                        future.completeExceptionally(
                                new IOException("OSRS Wiki returned an empty JSON response")
                        );

                        return;
                    }

                    if (json.has("error") && !json.get("error").isJsonNull()) {
                        future.completeExceptionally(
                                new IOException("OSRS Wiki Bucket error: " + json.get("error").getAsString())
                        );

                        return;
                    }

                    future.complete(json);
                }
            }
        });

        return future;
    }

    private JsonArray getBucketResults(JsonObject response) {
        if (response == null || !response.has("bucket")) {
            return null;
        }

        JsonElement bucket = response.get("bucket");

        if (bucket == null || !bucket.isJsonArray()) {
            return null;
        }

        return bucket.getAsJsonArray();
    }

    /**
     * Joined Bucket fields are normally returned using their
     * fully-qualified name.
     *
     * Accept a few formats so minor API serialization differences
     * do not break the feature.
     */
    private int getItemId(JsonObject result) {
        JsonElement itemIdElement = null;

        if (result.has("infobox_item.item_id")) {
            itemIdElement = result.get("infobox_item.item_id");
        } else if (result.has("item_id")) {
            itemIdElement = result.get("item_id");
        }

        return parseFirstPositiveInteger(itemIdElement);
    }

    private int parseFirstPositiveInteger(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return -1;
        }

        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                int itemId = parseFirstPositiveInteger(value);

                if (itemId > 0) {
                    return itemId;
                }
            }

            return -1;
        }

        if (!element.isJsonPrimitive()) {
            return -1;
        }

        String value = element.getAsString();

        if (value == null || value.trim().isEmpty()) {
            return -1;
        }

        /*
         * Repeated Bucket fields may occasionally be represented
         * as a string rather than a JSON array.
         *
         * Extract the first positive integer safely.
         */
        String[] parts = value.split("[^0-9]+");

        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }

            try {
                int itemId = Integer.parseInt(part);

                if (itemId > 0) {
                    return itemId;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return -1;
    }

    private String getString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return null;
        }

        JsonElement element = object.get(key);

        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }

        return element.getAsString();
    }

    private boolean getBoolean(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return false;
        }

        JsonElement element = object.get(key);

        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return false;
        }

        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Bucket queries use Lua strings.
     */
    private String escapeBucketString(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'");
    }
}
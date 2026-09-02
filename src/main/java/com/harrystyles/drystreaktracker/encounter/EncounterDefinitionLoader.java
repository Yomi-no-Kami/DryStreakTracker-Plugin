package com.harrystyles.drystreaktracker.encounter;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

/**
 * Loads encounter definitions from JSON resources.
 * <p>
 * encounters.json
 * -> NpcLootReceived / NPC ID based encounters.
 * <p>
 * loot-received-encounters.json
 * -> LootReceived / source-name based encounters.
 * <p>
 * The JSON definitions do NOT need a lootType property.
 * The loader assigns the correct type automatically based
 * on the resource being loaded.
 */
@Slf4j
@Singleton
public class EncounterDefinitionLoader {
    private static final String NPC_RESOURCE = "/encounters.json";

    private static final String LOOT_RECEIVED_RESOURCE = "/loot-received-encounters.json";


    private final Gson gson;


    @Inject
    public EncounterDefinitionLoader(Gson gson) {
        this.gson = gson;
    }


    /**
     * Loads both encounter resources into the registry.
     */
    public void loadInto(EncounterRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("EncounterRegistry cannot be null");
        }

        registry.clear();

        /*
         * Normal NPC death encounters.
         */
        EncounterDefinition[] npcDefinitions = loadResource(NPC_RESOURCE, EncounterLootType.GROUND_LOOT);

        for (EncounterDefinition definition : npcDefinitions) {
            validate(definition);

            registry.register(definition);
        }

        /*
         * Generic LootReceived encounters.
         */
        EncounterDefinition[] lootReceivedDefinitions = loadResource(LOOT_RECEIVED_RESOURCE, EncounterLootType.LOOT_RECEIVED);

        for (EncounterDefinition definition : lootReceivedDefinitions) {
            validate(definition);

            registry.register(definition);
        }

        log.info("Encounter registry initialized with {} encounters", registry.size());
    }


    /**
     * Loads one JSON resource and assigns every encounter
     * in that file the supplied loot type.
     */
    private EncounterDefinition[] loadResource(String resource, EncounterLootType lootType) {
        InputStream inputStream = getClass().getResourceAsStream(resource);

        if (inputStream == null) {
            /**
             * encounters.json is required.
             *
             * loot-received-encounters.json may legitimately be
             * empty/not added yet during development, so return an
             * empty array for that file.
             */
            if (lootType == EncounterLootType.LOOT_RECEIVED) {
                log.warn("Optional LootReceived encounter resource not found: {}", resource);

                return new EncounterDefinition[0];
            }

            throw new IllegalStateException("Encounter resource not found: " + resource);
        }

        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            EncounterDefinition[] definitions = gson.fromJson(reader, EncounterDefinition[].class);

            if (definitions == null) {
                return new EncounterDefinition[0];
            }

            for (EncounterDefinition definition : definitions) {
                if (definition == null) {
                    continue;
                }

                /**
                 * Important:
                 *
                 * Ignore any lootType from JSON and force the
                 * correct type based on the resource.
                 */
                definition.setLootType(lootType);
            }

            log.info("Loaded {} encounters from {} as {}", definitions.length, resource, lootType);

            return definitions;
        } catch (JsonParseException e) {
            throw new IllegalStateException("Invalid encounter JSON: " + resource, e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load encounter JSON: " + resource, e);
        }
    }


    private void validate(EncounterDefinition definition) {
        if (definition == null) {
            throw new IllegalStateException("Encounter definition cannot be null");
        }

        if (definition.getEncounterId() == null || definition.getEncounterId().trim().isEmpty()) {
            throw new IllegalStateException("Encounter definition has no encounterId");
        }

        if (definition.getDisplayName() == null || definition.getDisplayName().trim().isEmpty()) {
            throw new IllegalStateException("Encounter " + definition.getEncounterId() + " has no displayName");
        }

        String imageFileName = definition.getImageFileName();

        if (imageFileName != null && !imageFileName.trim().isEmpty()) {
            String fileName = imageFileName.trim();

            if (fileName.contains("/") || fileName.contains("\\") || fileName.contains(":")) {
                throw new IllegalStateException(
                        "[DRY STREAK TRACKER]: Encounter " + definition.getEncounterId() + " has an invalid imageFileName: " + fileName);
            }
        }

        if (definition.getLootType() == null) {
            throw new IllegalStateException("Encounter " + definition.getEncounterId() + " has no internally assigned loot type");
        }

        if (definition.getTrackedDropIds() == null) {
            throw new IllegalStateException("Encounter " + definition.getEncounterId() + " has null trackedDropIds");
        }

        if (definition.getPetDropIds() == null) {
            throw new IllegalStateException("Encounter " + definition.getEncounterId() + " has null petDropIds");
        }

        /**
         * encounters.json
         *
         * Must identify an NPC by NPC ID.
         */
        if (definition.getLootType() == EncounterLootType.GROUND_LOOT) {
            if (definition.getNpcIds().isEmpty()) {
                throw new IllegalStateException("NPC encounter " + definition.getEncounterId() + " from " + NPC_RESOURCE + " must have at least one npcId"
                );
            }

            /**
             * Prevent accidentally putting LootReceived
             * configuration in encounters.json.
             */
            if (!definition.getLootSourceNames().isEmpty()) {
                log.warn("Encounter {} is in {} but contains lootSourceNames. " + "lootSourceNames will not be used.", definition.getEncounterId(), NPC_RESOURCE);
            }

            return;
        }

        /**
         * loot-received-encounters.json
         *
         * Must identify the encounter using the name returned by
         * LootReceived.getName().
         */
        if (definition.getLootType() == EncounterLootType.LOOT_RECEIVED) {
            if (definition.getLootSourceNames().isEmpty()) {
                throw new IllegalStateException("LootReceived encounter " + definition.getEncounterId() + " from " + LOOT_RECEIVED_RESOURCE + " must have at least one lootSourceName");
            }
        }
    }
}
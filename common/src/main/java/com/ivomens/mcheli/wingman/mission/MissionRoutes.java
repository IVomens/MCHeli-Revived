package com.ivomens.mcheli.wingman.mission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.ivomens.mcheli.core.wingman.mission.MissionNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Named node-mission routes, shared by every world: {@code config/wingman_routes.json} maps each name
 * to its nodes in their text form, as it always has. Who created each route is kept next to it in
 * {@code wingman_route_owners.json}, so the route file keeps its old format. The files are read when
 * a server starts and written on every change.
 */
public final class MissionRoutes {

    private static final Logger LOG = LogManager.getLogger("McHeliWingman");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ROUTES_TYPE = new TypeToken<Map<String, List<String>>>() {
    }.getType();
    private static final Type OWNERS_TYPE = new TypeToken<Map<String, UUID>>() {
    }.getType();

    private final Path routesFile;
    private final Path ownersFile;
    private final Map<String, List<String>> routes = new LinkedHashMap<>();
    private final Map<String, UUID> owners = new LinkedHashMap<>();

    private MissionRoutes(Path configDir) {
        this.routesFile = configDir.resolve("wingman_routes.json");
        this.ownersFile = configDir.resolve("wingman_route_owners.json");
    }

    /** The routes saved in this config directory. */
    public static MissionRoutes load(Path configDir) {
        MissionRoutes missionRoutes = new MissionRoutes(configDir);
        missionRoutes.read();
        return missionRoutes;
    }

    /** Saves the route. A route that already exists keeps its owner. */
    public void put(String name, List<MissionNode> nodes, UUID owner) {
        List<String> serialized = new ArrayList<>(nodes.size());
        for (MissionNode node : nodes) {
            serialized.add(node.serialize());
        }
        if (this.routes.put(name, serialized) == null) {
            this.owners.put(name, owner);
        }
        write();
    }

    public boolean exists(String name) {
        return this.routes.containsKey(name);
    }

    /** Player who created the route; null for routes from before owners were recorded. */
    public @Nullable UUID owner(String name) {
        return this.owners.get(name);
    }

    /** Parsed copy of the named route, or null if it does not exist. */
    public @Nullable List<MissionNode> get(String name) {
        List<String> raw = this.routes.get(name);
        if (raw == null) {
            return null;
        }
        List<MissionNode> nodes = new ArrayList<>(raw.size());
        for (String token : raw) {
            nodes.add(MissionNode.parse(token));
        }
        return nodes;
    }

    public boolean remove(String name) {
        if (this.routes.remove(name) == null) {
            return false;
        }
        this.owners.remove(name);
        write();
        return true;
    }

    public Set<String> names() {
        return new LinkedHashSet<>(this.routes.keySet());
    }

    private void read() {
        if (!Files.exists(this.routesFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(this.routesFile, StandardCharsets.UTF_8)) {
            Map<String, List<String>> loaded = GSON.fromJson(reader, ROUTES_TYPE);
            if (loaded != null) {
                this.routes.putAll(loaded);
            }
            LOG.info("[MissionPlan] Loaded {} route(s) from {}", this.routes.size(), this.routesFile.getFileName());
        } catch (Exception e) {
            LOG.warn("[MissionPlan] Failed to load routes: {}", e.getMessage());
        }
        readOwners();
    }

    private void readOwners() {
        if (!Files.exists(this.ownersFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(this.ownersFile, StandardCharsets.UTF_8)) {
            Map<String, UUID> loaded = GSON.fromJson(reader, OWNERS_TYPE);
            if (loaded != null) {
                this.owners.putAll(loaded);
            }
        } catch (Exception e) {
            LOG.warn("[MissionPlan] Failed to load route owners: {}", e.getMessage());
        }
    }

    private void write() {
        try (Writer writer = Files.newBufferedWriter(this.routesFile, StandardCharsets.UTF_8)) {
            GSON.toJson(this.routes, writer);
        } catch (Exception e) {
            LOG.warn("[MissionPlan] Failed to save routes: {}", e.getMessage());
        }
        try (Writer writer = Files.newBufferedWriter(this.ownersFile, StandardCharsets.UTF_8)) {
            GSON.toJson(this.owners, writer);
        } catch (Exception e) {
            LOG.warn("[MissionPlan] Failed to save route owners: {}", e.getMessage());
        }
    }
}

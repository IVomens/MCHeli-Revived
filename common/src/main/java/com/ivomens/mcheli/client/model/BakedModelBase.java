package com.ivomens.mcheli.client.model;

import com.ivomens.mcheli.client.render.LegacyGL;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base of the baked model formats. A loaded model is a list of {@link GroupObject}s in file order
 * plus a case-insensitive index of named {@link BakedPart}s. All render methods emit through the
 * current {@link LegacyGL} frame.
 * <p>
 * Part names follow the MCHeli convention: {@code $body} is the airframe and {@code $<name>} is a
 * movable part named in the vehicle definition. {@link #findPrefixedPart(String)} resolves such a
 * part from the bare definition name without building the {@code "$" + name} string per frame.
 */
public abstract class BakedModelBase implements IModelCustom {

    /** Groups whose name contains this hold the tread template of crawler tracks. */
    private static final String TRACK_TEMPLATE_MARKER = "crawler_track";
    private static final float BOUNDS_UNSET = 100000.0F;

    // The box around the vertices read so far; published as bounds() once the file is read.
    private float minX = BOUNDS_UNSET;
    private float minY = BOUNDS_UNSET;
    private float minZ = BOUNDS_UNSET;
    private float maxX = -BOUNDS_UNSET;
    private float maxY = -BOUNDS_UNSET;
    private float maxZ = -BOUNDS_UNSET;
    private ModelBounds bounds = new ModelBounds(BOUNDS_UNSET, BOUNDS_UNSET, BOUNDS_UNSET, -BOUNDS_UNSET,
            -BOUNDS_UNSET, -BOUNDS_UNSET);

    private List<GroupObject> groups = List.of();
    /** One part per top-level object in file order, as drawn by {@link #renderStatic}. */
    private List<BakedPart> topLevelParts = List.of();
    private NavigableMap<String, BakedPart> partsByName = Collections.emptyNavigableMap();
    /** {@code $}-prefixed parts keyed by their name without the {@code $}. */
    private NavigableMap<String, BakedPart> partsByBareName = Collections.emptyNavigableMap();
    /**
     * {@link #findPrefixedPart} answers by the exact name asked for: the renderers ask for the same
     * definition names every frame, and a hash lookup costs far less than the case-insensitive
     * tree's string comparisons. Instancing workers ask too, so it is concurrent.
     */
    private Map<String, Optional<BakedPart>> prefixedLookups = new ConcurrentHashMap<>();
    /** All groups as one part, made the first time it is asked for. */
    @Nullable
    private BakedPart wholeModel;
    @Nullable
    private BakedPart trackTemplate;
    @Nullable
    private StaticTracks staticTracks;

    // Bounds

    /** A vertex was read: the bounds grow to hold it. */
    protected void checkMinMax(ModelVertex v) {
        this.minX = Math.min(this.minX, v.x());
        this.minY = Math.min(this.minY, v.y());
        this.minZ = Math.min(this.minZ, v.z());
        this.maxX = Math.max(this.maxX, v.x());
        this.maxY = Math.max(this.maxY, v.y());
        this.maxZ = Math.max(this.maxZ, v.z());
    }

    /** The file has been read: publishes the bounds of its vertices. */
    protected void checkMinMaxFinal() {
        this.bounds = new ModelBounds(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    /** The box around every vertex of the model. */
    public ModelBounds bounds() {
        return this.bounds;
    }

    /** Upper bound of any vertex's distance from the model origin; used for render culling boxes. */
    public float getBoundingRadius() {
        ModelBounds b = this.bounds;
        if (b.maxX() < b.minX()) {
            return 0.0F;
        }
        float x = Math.max(Math.abs(b.minX()), Math.abs(b.maxX()));
        float y = Math.max(Math.abs(b.minY()), Math.abs(b.maxY()));
        float z = Math.max(Math.abs(b.minZ()), Math.abs(b.maxZ()));
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    /** Half the diagonal of the model's bounding box: its size, independent of where the origin is. */
    public float getExtentRadius() {
        return BakedPart.halfDiagonal(this.bounds.sizeX(), this.bounds.sizeY(), this.bounds.sizeZ());
    }

    // Part index

    /**
     * Installs the loaded groups and builds the part index.
     *
     * @param followersJoinPart whether groups without a {@code $} name belong to the {@code $} part
     *                          before them (Metasequoia splits one part into several objects)
     */
    protected final void indexGroups(List<GroupObject> loadedGroups, boolean followersJoinPart) {
        Map<String, List<GroupObject>> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<BakedPart> topLevel = topLevelParts(loadedGroups, followersJoinPart, byName);

        NavigableMap<String, BakedPart> parts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        NavigableMap<String, BakedPart> bareParts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        byName.forEach((name, list) -> {
            BakedPart part = new BakedPart(name, list.toArray(GroupObject[]::new));
            parts.put(name, part);
            if (isPrefixed(name)) {
                bareParts.put(name.substring(1), part);
            }
        });

        BakedPart template = null;
        for (BakedPart part : topLevel) {
            if (part.name().contains(TRACK_TEMPLATE_MARKER)) {
                template = part;
            }
        }

        this.setGroups(Collections.unmodifiableList(new ArrayList<>(loadedGroups)));
        this.topLevelParts = List.copyOf(topLevel);
        this.partsByName = Collections.unmodifiableNavigableMap(parts);
        this.partsByBareName = Collections.unmodifiableNavigableMap(bareParts);
        this.prefixedLookups = new ConcurrentHashMap<>();
        this.trackTemplate = template;
    }

    /**
     * The parts drawn at the top level, in file order, collecting every group under its name in
     * {@code byName} on the way. With {@code followersJoinPart}, a {@code $} group and the groups after it
     * up to the next one form one part, and the {@code $} name also stands for that whole segment.
     */
    private static List<BakedPart> topLevelParts(List<GroupObject> loadedGroups, boolean followersJoinPart,
                                                 Map<String, List<GroupObject>> byName) {
        List<BakedPart> topLevel = new ArrayList<>();
        List<GroupObject> segment = null;
        String segmentName = null;
        for (GroupObject group : loadedGroups) {
            boolean leader = isPrefixed(group.name);
            if (followersJoinPart && leader) {
                if (segment != null) {
                    topLevel.add(new BakedPart(segmentName, segment.toArray(GroupObject[]::new)));
                }
                segment = new ArrayList<>();
                segmentName = group.name;
                segment.add(group);
                byName.computeIfAbsent(group.name, k -> new ArrayList<>());
                continue;
            }
            byName.computeIfAbsent(group.name, k -> new ArrayList<>()).add(group);
            if (followersJoinPart && segment != null) {
                segment.add(group);
            } else {
                topLevel.add(new BakedPart(group.name, new GroupObject[]{group}));
            }
        }
        if (segment != null) {
            topLevel.add(new BakedPart(segmentName, segment.toArray(GroupObject[]::new)));
        }
        if (followersJoinPart) {
            // a $ name stands for its whole segment; repeated names render every segment
            for (BakedPart part : topLevel) {
                if (isPrefixed(part.name())) {
                    List<GroupObject> list = byName.get(part.name());
                    Collections.addAll(list, part.groupsForIndex());
                }
            }
        }
        return topLevel;
    }

    /** Shares another model's baked data and index (both are immutable once loaded). */
    protected final void adoptIndex(BakedModelBase source) {
        this.setGroups(source.groups);
        this.topLevelParts = source.topLevelParts;
        this.partsByName = source.partsByName;
        this.partsByBareName = source.partsByBareName;
        this.prefixedLookups = new ConcurrentHashMap<>();
        this.trackTemplate = source.trackTemplate;
    }

    protected final void clearIndex() {
        this.setGroups(List.of());
        this.topLevelParts = List.of();
        this.partsByName = Collections.emptyNavigableMap();
        this.partsByBareName = Collections.emptyNavigableMap();
        this.prefixedLookups = new ConcurrentHashMap<>();
        this.trackTemplate = null;
    }

    /** Installs the groups and forgets everything built from the previous ones. */
    private void setGroups(List<GroupObject> groups) {
        this.groups = groups;
        this.wholeModel = null;
        this.staticTracks = null;
    }

    private static boolean isPrefixed(String name) {
        return !name.isEmpty() && name.charAt(0) == '$';
    }

    /**
     * The parts as they appear in the file. With Metasequoia models a {@code $} part includes the
     * unnamed objects that follow it.
     */
    public List<BakedPart> topLevelParts() {
        return this.topLevelParts;
    }

    /** The part with this name (case-insensitive), or {@code null}. */
    @Nullable
    public BakedPart findPart(String name) {
        return this.partsByName.get(name);
    }

    /** The part {@code "$" + bareName} (case-insensitive), or {@code null}; allocation free. */
    @Nullable
    public BakedPart findPrefixedPart(String bareName) {
        Optional<BakedPart> known = this.prefixedLookups.get(bareName);
        if (known == null) {
            known = Optional.ofNullable(this.partsByBareName.get(bareName));
            this.prefixedLookups.put(bareName, known);
        }
        return known.orElse(null);
    }

    public boolean containsPart(String partName) {
        return this.partsByName.containsKey(partName);
    }

    /** The whole model as one part, in file order: what {@link #renderAll()} draws. */
    public BakedPart wholeModel() {
        BakedPart whole = this.wholeModel;
        if (whole == null) {
            whole = new BakedPart("", this.groups.toArray(GroupObject[]::new));
            this.wholeModel = whole;
        }
        return whole;
    }

    // Rendering

    @Override
    public void renderAll() {
        for (GroupObject group : this.groups) {
            group.render();
        }
    }

    @Override
    public void renderPart(String partName) {
        BakedPart part = this.findPart(partName);
        if (part != null) {
            part.render();
        }
    }

    /** Renders faces {@code [startFace, maxFace]} (1-based counter over all groups); used by the drawing animation. */
    public void renderAll(int startFace, int maxFace) {
        startFace = Math.max(startFace, 0);
        int faceCount = 0;
        for (GroupObject group : this.groups) {
            int faces = group.getBakedVertexCount() / 4;
            if (faces == 0) {
                continue;
            }
            int first = Math.max(startFace, faceCount + 1);
            int last = Math.min(maxFace, faceCount + faces);
            if (first <= last) {
                LegacyGL.emitBaked(group.getBakedData(), (first - faceCount - 1) * 4, (last - first + 1) * 4);
            }
            faceCount += faces;
            if (faceCount >= maxFace) {
                return;
            }
        }
    }

    /** Draws the triangle edges of the first {@code maxLine / 3} faces as a wireframe. */
    public void renderAllLine(int startLine, int maxLine) {
        int maxFaces = maxLine / 3;
        int faceCount = 0;
        for (GroupObject group : this.groups) {
            int faces = group.getBakedVertexCount() / 4;
            if (faces == 0) {
                continue;
            }
            int count = Math.min(faces, maxFaces - faceCount);
            if (count <= 0) {
                return;
            }
            LegacyGL.emitBakedLines(group.getBakedData(), 0, count * 4);
            faceCount += count;
        }
    }

    /** Renders every part at rest except the crawler track template. */
    public void renderStaticParts() {
        for (BakedPart part : this.topLevelParts) {
            if (!part.name().contains(TRACK_TEMPLATE_MARKER)) {
                part.render();
            }
        }
    }

    /** Whether the model has a crawler track tread to lay along a track path. */
    public boolean hasTrackTemplate() {
        return this.trackTemplate != null;
    }

    /** The baked vertices of the crawler track tread; only when {@link #hasTrackTemplate()}. */
    public float[] trackTreadVertices() {
        return this.trackTemplate.bakedData();
    }

    /** The static crawler track mesh laid out for this model, or null until one is set. */
    @Nullable
    public PartMesh staticTracks() {
        return this.staticTracks;
    }

    /** Keeps the static crawler track mesh built from this model's tread until the model is reloaded. */
    public PartMesh setStaticTracks(float[] vertices) {
        this.staticTracks = new StaticTracks(vertices);
        return this.staticTracks;
    }

    /** The tracks are drawn in full at any distance; they are already a fraction of the animated links. */
    private record StaticTracks(float[] vertices) implements PartMesh {

        @Override
        public void render() {
            LegacyGL.emitBaked(this.vertices, 0, this.vertices.length / LegacyGL.BAKED_STRIDE);
        }

        @Override
        public float[] vertices(int detailLevel) {
            return this.vertices;
        }
    }

    public abstract int getVertexNum();

    public abstract int getFaceNum();

    /** Collapses runs of spaces and tabs to one space and trims the line. */
    protected static String normalizeWhitespace(String line) {
        StringBuilder sb = new StringBuilder(line.length());
        boolean lastWasWhitespace = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t') {
                if (!lastWasWhitespace) {
                    sb.append(' ');
                    lastWasWhitespace = true;
                }
            } else {
                sb.append(c);
                lastWasWhitespace = false;
            }
        }
        int start = 0;
        int end = sb.length();
        while (start < end && sb.charAt(start) == ' ') {
            start++;
        }
        while (end > start && sb.charAt(end - 1) == ' ') {
            end--;
        }
        return sb.substring(start, end);
    }
}

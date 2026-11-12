package com.ivomens.mcheli.client.model;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Wavefront .obj model. Parsed once (no GL context required) and baked into per-group vertex
 * arrays. Only {@code g}/{@code o} lines whose name starts with {@code $} open a new group; faces
 * before the first such line go to a group called {@code Default}.
 */
public class WavefrontModel extends BakedModelBase {

    private static final Pattern VERTEX = Pattern.compile("v( -?\\d+\\.\\d+){3,4} *");
    private static final Pattern VERTEX_NORMAL = Pattern.compile("vn( -?\\d+\\.\\d+){3,4} *");
    private static final Pattern TEXTURE_COORDINATE = Pattern.compile("vt( -?\\d+\\.\\d+){2,3} *");
    private static final Pattern FACE_V_VT_VN = Pattern.compile("f( \\d+/\\d+/\\d+){3,4} *");
    private static final Pattern FACE_V_VT = Pattern.compile("f( \\d+/\\d+){3,4} *");
    private static final Pattern FACE_V_VN = Pattern.compile("f( \\d+//\\d+){3,4} *");
    private static final Pattern FACE_V = Pattern.compile("f( \\d+){3,4} *");
    private static final Pattern GROUP = Pattern.compile("[go]( [-$\\w\\d]+) *");

    private enum FaceFormat {V_VT_VN, V_VT, V_VN, V}

    private final String fileName;
    private int vertexNum;

    /** Parse-time state, dropped once the groups are baked. */
    private List<ModelVertex> vertices = new ArrayList<>();
    private List<ModelVertex> vertexNormals = new ArrayList<>();
    private List<TextureCoordinate> textureCoordinates = new ArrayList<>();
    private GroupObject currentGroup;

    public WavefrontModel(ResourceLocation location, Resource resource) throws ModelFormatException {
        this.fileName = location.toString();
        try {
            this.load(resource.open());
        } catch (IOException e) {
            throw new ModelFormatException("IO Exception reading model format", e);
        }
    }

    private void load(InputStream inputStream) throws ModelFormatException {
        List<GroupObject> groups = new ArrayList<>();
        int lineNumber = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = normalizeWhitespace(line);
                if (!line.isEmpty() && !line.startsWith("#")) {
                    this.parseLine(line, lineNumber, groups);
                }
            }
            if (this.currentGroup != null) {
                groups.add(this.currentGroup);
            }
        } catch (IOException e) {
            throw new ModelFormatException("IO Exception reading model format", e);
        } finally {
            this.checkMinMaxFinal();
        }

        this.vertexNum = this.vertices.size();
        for (GroupObject group : groups) {
            group.releaseFaces();
        }
        this.vertices = null;
        this.vertexNormals = null;
        this.textureCoordinates = null;
        this.currentGroup = null;
        this.indexGroups(groups, false);
    }

    /** One non-empty line: a vertex, a face of the current group, or a {@code $} group starting a new one. */
    private void parseLine(String line, int lineNumber, List<GroupObject> groups) {
        switch (line.charAt(0)) {
            case 'v' -> this.parseVertexLine(line, lineNumber);
            case 'f' -> {
                if (this.currentGroup == null) {
                    this.currentGroup = new GroupObject("Default");
                }
                this.currentGroup.faces.add(this.parseFace(line, lineNumber));
            }
            case 'g', 'o' -> {
                if (line.length() > 2 && line.charAt(2) == '$') {
                    GroupObject group = this.parseGroup(line, lineNumber);
                    if (this.currentGroup != null) {
                        groups.add(this.currentGroup);
                    }
                    this.currentGroup = group;
                }
            }
            default -> {
            }
        }
    }

    private void parseVertexLine(String line, int lineNumber) {
        if (line.length() < 2) {
            return;
        }
        switch (line.charAt(1)) {
            case ' ' -> {
                ModelVertex vertex = this.parseVertex(line, lineNumber);
                if (vertex != null) {
                    this.checkMinMax(vertex);
                    this.vertices.add(vertex);
                }
            }
            case 'n' -> {
                ModelVertex normal = this.parseVertexNormal(line, lineNumber);
                if (normal != null) {
                    this.vertexNormals.add(normal);
                }
            }
            case 't' -> {
                TextureCoordinate coordinate = this.parseTextureCoordinate(line, lineNumber);
                if (coordinate != null) {
                    this.textureCoordinates.add(coordinate);
                }
            }
            default -> {
            }
        }
    }

    private String[] tokensAfterKeyword(String line, Pattern format, int lineNumber) {
        if (!format.matcher(line).matches()) {
            throw this.formatError(line, lineNumber, "Incorrect format");
        }
        return line.substring(line.indexOf(' ') + 1).split(" ");
    }

    private ModelVertex parseVertex(String line, int lineNumber) {
        String[] tokens = this.tokensAfterKeyword(line, VERTEX, lineNumber);
        try {
            if (tokens.length == 2) {
                return new ModelVertex(Float.parseFloat(tokens[0]), Float.parseFloat(tokens[1]));
            }
            if (tokens.length >= 3) {
                return new ModelVertex(Float.parseFloat(tokens[0]), Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]));
            }
        } catch (NumberFormatException e) {
            throw new ModelFormatException(String.format("Number formatting error at line %d", lineNumber), e);
        }
        return null;
    }

    private ModelVertex parseVertexNormal(String line, int lineNumber) {
        String[] tokens = this.tokensAfterKeyword(line, VERTEX_NORMAL, lineNumber);
        try {
            if (tokens.length >= 3) {
                return new ModelVertex(Float.parseFloat(tokens[0]), Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]));
            }
        } catch (NumberFormatException e) {
            throw new ModelFormatException(String.format("Number formatting error at line %d", lineNumber), e);
        }
        return null;
    }

    /** V is flipped: .obj puts the origin at the bottom left, Minecraft at the top left. */
    private TextureCoordinate parseTextureCoordinate(String line, int lineNumber) {
        String[] tokens = this.tokensAfterKeyword(line, TEXTURE_COORDINATE, lineNumber);
        try {
            if (tokens.length == 2) {
                return new TextureCoordinate(Float.parseFloat(tokens[0]), 1.0F - Float.parseFloat(tokens[1]));
            }
            if (tokens.length == 3) {
                return new TextureCoordinate(Float.parseFloat(tokens[0]), 1.0F - Float.parseFloat(tokens[1]),
                        Float.parseFloat(tokens[2]));
            }
        } catch (NumberFormatException e) {
            throw new ModelFormatException(String.format("Number formatting error at line %d", lineNumber), e);
        }
        return null;
    }

    private static FaceFormat faceFormat(String line) {
        if (FACE_V_VT_VN.matcher(line).matches()) {
            return FaceFormat.V_VT_VN;
        }
        if (FACE_V_VT.matcher(line).matches()) {
            return FaceFormat.V_VT;
        }
        if (FACE_V_VN.matcher(line).matches()) {
            return FaceFormat.V_VN;
        }
        if (FACE_V.matcher(line).matches()) {
            return FaceFormat.V;
        }
        return null;
    }

    /** Triangles and quads both bake to quads, so a group may freely mix them. */
    private ModelFace parseFace(String line, int lineNumber) {
        FaceFormat format = faceFormat(line);
        if (format == null) {
            throw this.formatError(line, lineNumber, "Incorrect format");
        }
        String[] tokens = line.substring(line.indexOf(' ') + 1).split(" ");
        int corners = tokens.length;
        ModelVertex[] vertices = new ModelVertex[corners];
        TextureCoordinate[] uvs = format == FaceFormat.V_VT_VN || format == FaceFormat.V_VT
                ? new TextureCoordinate[corners] : null;
        ModelVertex[] normals = format == FaceFormat.V_VT_VN || format == FaceFormat.V_VN
                ? new ModelVertex[corners] : null;
        try {
            for (int i = 0; i < corners; i++) {
                String[] indices = tokens[i].split(format == FaceFormat.V_VN ? "//" : "/");
                vertices[i] = this.vertices.get(Integer.parseInt(indices[0]) - 1);
                switch (format) {
                    case V_VT_VN -> {
                        uvs[i] = this.textureCoordinates.get(Integer.parseInt(indices[1]) - 1);
                        normals[i] = this.vertexNormals.get(Integer.parseInt(indices[2]) - 1);
                    }
                    case V_VT -> uvs[i] = this.textureCoordinates.get(Integer.parseInt(indices[1]) - 1);
                    case V_VN -> normals[i] = this.vertexNormals.get(Integer.parseInt(indices[1]) - 1);
                    case V -> {
                    }
                }
            }
        } catch (IndexOutOfBoundsException e) {
            throw new ModelFormatException("Error parsing entry ('" + line + "', line " + lineNumber + ") in file '"
                    + this.fileName + "' - Index out of range", e);
        }
        return new ModelFace(vertices, uvs, normals, null);
    }

    private GroupObject parseGroup(String line, int lineNumber) {
        if (!GROUP.matcher(line).matches()) {
            throw this.formatError(line, lineNumber, "Incorrect format");
        }
        return new GroupObject(line.substring(line.indexOf(' ') + 1));
    }

    private ModelFormatException formatError(String line, int lineNumber, String reason) {
        return new ModelFormatException("Error parsing entry ('" + line + "', line " + lineNumber + ") in file '"
                + this.fileName + "' - " + reason);
    }

    @Override
    public String getType() {
        return "obj";
    }

    @Override
    public int getVertexNum() {
        return this.vertexNum;
    }

    @Override
    public int getFaceNum() {
        return this.vertexNum / 3;
    }
}

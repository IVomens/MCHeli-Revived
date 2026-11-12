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

/**
 * Metasequoia .mqo model. Parsed once (no GL context required) and baked into per-object vertex
 * arrays. Coordinates are divided by 100 (Metasequoia works in centimetres). An object whose name
 * starts with {@code $} opens a part; the unnamed objects after it belong to the same part.
 */
public class MetasequoiaModel extends BakedModelBase {

    private static final float UNITS_PER_BLOCK = 100.0F;
    /** Smoothing angle used when an object has no {@code facet} line: 45 degrees. */
    private static final double DEFAULT_FACET_COS = Math.cos(0.785398163375);
    private static final TextureCoordinate NO_UV = new TextureCoordinate(0.0F, 0.0F);

    private final String fileName;
    private int vertexNum;
    private int faceNum;

    /** Vertices of the object being parsed. */
    private final List<ModelVertex> vertices = new ArrayList<>();
    private int lineNumber;

    public MetasequoiaModel(ResourceLocation location, Resource resource) throws ModelFormatException {
        this.fileName = location.toString();
        try {
            this.load(resource.open());
        } catch (IOException e) {
            throw new ModelFormatException("IO Exception reading model format:" + this.fileName, e);
        }
    }

    private void load(InputStream inputStream) throws ModelFormatException {
        List<GroupObject> groups = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = this.nextLine(reader)) != null) {
                if (isObjectLine(line)) {
                    GroupObject group = this.parseObject(reader, line);
                    if (group != null) {
                        groups.add(group);
                    }
                }
            }
        } catch (IOException e) {
            throw new ModelFormatException("IO Exception reading model format : " + this.fileName, e);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            throw new ModelFormatException("format error : " + this.fileName + " : line=" + this.lineNumber, e);
        } finally {
            this.checkMinMaxFinal();
            this.vertices.clear();
        }

        for (GroupObject group : groups) {
            group.releaseFaces();
        }
        this.indexGroups(groups, true);
    }

    private String nextLine(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        this.lineNumber++;
        return normalizeWhitespace(line);
    }

    private static boolean isObjectLine(String line) {
        String[] s = line.split(" ");
        return s.length >= 2 && s[0].equals("Object") && s[1].length() >= 4 && s[1].charAt(0) == '"';
    }

    /** Reads one {@code Object "name" { ... }} chunk; returns null for an object without a name. */
    private GroupObject parseObject(BufferedReader reader, String header) throws IOException {
        String quotedName = header.split(" ")[1];
        String name = quotedName.substring(1, quotedName.length() - 1);
        if (name.isEmpty()) {
            return null;
        }
        GroupObject group = new GroupObject(name);
        this.vertices.clear();

        boolean smoothShading = false;
        double facetCos = DEFAULT_FACET_COS;
        int vertexCount = 0;
        String line;
        while ((line = this.nextLine(reader)) != null) {
            if (line.equalsIgnoreCase("shading 1")) {
                smoothShading = true;
            }
            String[] s = line.split(" ");
            if (s.length == 2 && s[0].equalsIgnoreCase("facet")) {
                facetCos = Math.cos(Double.parseDouble(s[1]) * 3.1415926535 / 180.0);
            }
            if (isChunkLine(line, "vertex")) {
                vertexCount = Integer.parseInt(line.split(" ")[1]);
                break;
            }
        }

        if (vertexCount > 0) {
            this.readVertices(reader, vertexCount);
            int faceCount = 0;
            while ((line = this.nextLine(reader)) != null) {
                if (isChunkLine(line, "face")) {
                    faceCount = Integer.parseInt(line.split(" ")[1]);
                    break;
                }
            }
            if (faceCount > 0) {
                this.readFaces(reader, faceCount, group);
                calculateVertexNormals(group, smoothShading, facetCos, this.vertices.size());
            }
        }

        this.vertexNum += this.vertices.size();
        this.faceNum += group.faces.size();
        this.vertices.clear();
        group.bake();
        return group;
    }

    private static boolean isChunkLine(String line, String keyword) {
        return line.startsWith(keyword + " ") || line.equals(keyword);
    }

    private void readVertices(BufferedReader reader, int count) throws IOException {
        String line;
        while ((line = this.nextLine(reader)) != null) {
            String[] s = line.split(" ");
            if (s.length == 3) {
                ModelVertex v = new ModelVertex(Float.parseFloat(s[0]) / UNITS_PER_BLOCK,
                        Float.parseFloat(s[1]) / UNITS_PER_BLOCK, Float.parseFloat(s[2]) / UNITS_PER_BLOCK);
                this.checkMinMax(v);
                this.vertices.add(v);
                if (--count <= 0) {
                    return;
                }
            } else if (s.length > 0) {
                throw new ModelFormatException("format error : " + this.fileName + " : line=" + this.lineNumber);
            }
        }
    }

    private void readFaces(BufferedReader reader, int count, GroupObject group) throws IOException {
        String line;
        while ((line = this.nextLine(reader)) != null) {
            String[] s = line.split(" ");
            if (s.length <= 2) {
                continue;
            }
            if (Integer.parseInt(s[0]) >= 3) {
                this.parseFace(line, group.faces);
            }
            if (--count <= 0) {
                return;
            }
        }
    }

    /**
     * Parses {@code n V(a b c [d]) M(m) UV(u v ...)}. Corners are stored in reverse order to flip
     * the winding, and quads are split into two triangles.
     */
    private void parseFace(String line, List<ModelFace> out) {
        String[] s = line.split("[ VU)(M]+");
        int corners = Integer.parseInt(s[0]);
        if (corners == 3) {
            out.add(this.triangle(s, 3, 2, 1, s.length >= 11 ? 9 : -1, 7, 5));
        } else if (corners == 4) {
            out.add(this.triangle(s, 3, 2, 1, s.length >= 12 ? 10 : -1, 8, 6));
            out.add(this.triangle(s, 4, 3, 1, s.length >= 14 ? 12 : -1, 10, 6));
        }
    }

    /**
     * One triangle from the split face line: {@code a, b, c} index the vertex ids, {@code uvA, uvB,
     * uvC} the u of each corner's uv pair (v follows it). A negative {@code uvA} means no uv data.
     */
    private ModelFace triangle(String[] s, int a, int b, int c, int uvA, int uvB, int uvC) {
        int[] ids = {Integer.parseInt(s[a]), Integer.parseInt(s[b]), Integer.parseInt(s[c])};
        ModelVertex[] corners = {this.vertices.get(ids[0]), this.vertices.get(ids[1]), this.vertices.get(ids[2])};
        TextureCoordinate[] uvs = uvA >= 0 ? new TextureCoordinate[]{uv(s, uvA), uv(s, uvB), uv(s, uvC)}
                : new TextureCoordinate[]{NO_UV, NO_UV, NO_UV};
        return new ModelFace(corners, uvs, null, ids);
    }

    private static TextureCoordinate uv(String[] s, int index) {
        return new TextureCoordinate(Float.parseFloat(s[index]), Float.parseFloat(s[index + 1]));
    }

    /**
     * Flat shading uses the face normal at every corner. Smooth shading averages the normals of all
     * faces sharing the corner's vertex that lie within the smoothing angle.
     */
    private static void calculateVertexNormals(GroupObject group, boolean smoothShading, double facetCos,
                                               int vertexCount) {
        List<List<ModelFace>> facesByVertex = smoothShading ? facesByVertex(group.faces, vertexCount) : null;
        for (ModelFace face : group.faces) {
            int[] ids = face.verticesID();
            ModelVertex[] normals = new ModelVertex[ids.length];
            for (int i = 0; i < ids.length; i++) {
                if (!smoothShading) {
                    normals[i] = face.faceNormal();
                    continue;
                }
                ModelVertex normal = averageNormal(face.faceNormal(), facesByVertex.get(ids[i]), (float) facetCos)
                        .normalized();
                boolean withinAngle = face.faceNormal().dot(normal) >= facetCos;
                normals[i] = withinAngle ? normal : face.faceNormal();
            }
            face.setVertexNormals(normals);
        }
    }

    /** For each vertex id, the faces using it in file order, each face once. */
    private static List<List<ModelFace>> facesByVertex(List<ModelFace> faces, int vertexCount) {
        List<List<ModelFace>> result = new ArrayList<>(vertexCount);
        for (int i = 0; i < vertexCount; i++) {
            result.add(new ArrayList<>(4));
        }
        for (ModelFace face : faces) {
            for (int id : face.verticesID()) {
                List<ModelFace> list = result.get(id);
                if (list.isEmpty() || list.get(list.size() - 1) != face) {
                    list.add(face);
                }
            }
        }
        return result;
    }

    private static ModelVertex averageNormal(ModelVertex faceNormal, List<ModelFace> neighbours, float facetCos) {
        ModelVertex sum = new ModelVertex(0.0F, 0.0F, 0.0F);
        for (ModelFace neighbour : neighbours) {
            ModelVertex n = neighbour.faceNormal();
            if (!(n.dot(faceNormal) < facetCos)) {
                sum = sum.plus(n);
            }
        }
        return sum.normalized();
    }

    @Override
    public String getType() {
        return "mqo";
    }

    @Override
    public int getVertexNum() {
        return this.vertexNum;
    }

    @Override
    public int getFaceNum() {
        return this.faceNum;
    }
}

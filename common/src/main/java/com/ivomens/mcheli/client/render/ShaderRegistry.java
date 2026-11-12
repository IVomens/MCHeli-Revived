package com.ivomens.mcheli.client.render;

import com.ivomens.mcheli.util.Ids;
import com.ivomens.mcheli.util.ModLogger;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Raw GLSL programs shipped under {@code assets/mcheli/shaders/} (not post effects; those are loaded
 * as vanilla post chains, see {@link com.ivomens.mcheli.wrapper.GameRendererHelper}).
 * <p>
 * Programs are compiled on the render thread the first time it runs after {@link #init()}; a failing
 * program is logged and skipped instead of crashing the client.
 */
public class ShaderRegistry {

    public static final String SHADER_PATH = "shaders/";
    private static final Map<String, ShaderProgram> SHADERS = new HashMap<>();

    /** Compiles the programs now if on the render thread, else as soon as the render thread runs. */
    public static void init() {
        if (RenderSystem.isOnRenderThread()) {
            registerShaders();
        } else {
            RenderSystem.recordRenderCall(ShaderRegistry::registerShaders);
        }
    }

    private static void registerShaders() {
        registerShader("basic");
    }

    private static void registerShader(String name) {
        ResourceLocation fragLoc = Ids.of(SHADER_PATH + name + ".frag");
        ResourceLocation vertLoc = Ids.of(SHADER_PATH + name + ".vert");
        Optional<Resource> shaderVert = Minecraft.getInstance().getResourceManager().getResource(vertLoc);
        Optional<Resource> shaderFrag = Minecraft.getInstance().getResourceManager().getResource(fragLoc);
        if (shaderVert.isEmpty() || shaderFrag.isEmpty()) {
            ModLogger.warn("Shader '{}' not found", name);
            return;
        }

        try (InputStream vs = shaderVert.get().open(); InputStream fs = shaderFrag.get().open()) {
            ShaderProgram old = SHADERS.put(name, new ShaderProgram(vs, vertLoc, fs, fragLoc));
            if (old != null) {
                old.cleanup();
            }
        } catch (Exception e) {
            ModLogger.warn("Failed to load shader '{}': {}", name, e.getMessage());
        }
    }

    private static String readStream(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** A compiled program by name (the file name without extension), or null if it failed or does not exist. */
    @Nullable
    public static ShaderProgram getShader(String key) {
        return SHADERS.get(key);
    }

    /** A linked GLSL program with cached uniform locations; render thread only. */
    public static class ShaderProgram {

        private final int programId;
        private final Map<String, Integer> uniformCache = new HashMap<>();

        public ShaderProgram(InputStream vertStream, ResourceLocation vertexPath, InputStream fragStream,
                             ResourceLocation fragPath) throws IOException {
            RenderSystem.assertOnRenderThread();
            int vert = compile(GL20.GL_VERTEX_SHADER, readStream(vertStream), vertexPath.toString());
            int frag;
            try {
                frag = compile(GL20.GL_FRAGMENT_SHADER, readStream(fragStream), fragPath.toString());
            } catch (RuntimeException e) {
                GL20.glDeleteShader(vert);
                throw e;
            }
            this.programId = GL20.glCreateProgram();
            GL20.glAttachShader(this.programId, vert);
            GL20.glAttachShader(this.programId, frag);
            GL20.glLinkProgram(this.programId);
            GL20.glDeleteShader(vert);
            GL20.glDeleteShader(frag);
            if (GL20.glGetProgrami(this.programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(this.programId, 1024);
                GL20.glDeleteProgram(this.programId);
                throw new IOException("Error linking shader " + vertexPath + "\n" + log);
            }
        }

        private static int compile(int type, String src, String path) {
            int shader = GL20.glCreateShader(type);
            GL20.glShaderSource(shader, src);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetShaderInfoLog(shader, 1024);
                GL20.glDeleteShader(shader);
                throw new RuntimeException("Error compiling shader " + path + "\n" + log);
            }
            return shader;
        }

        public void bind() {
            GL20.glUseProgram(this.programId);
        }

        public void unbind() {
            GL20.glUseProgram(0);
        }

        public void cleanup() {
            GL20.glDeleteProgram(this.programId);
        }

        public int getUniformLocation(String name) {
            return this.uniformCache.computeIfAbsent(name, n -> GL20.glGetUniformLocation(this.programId, n));
        }

        public void setUniform(String name, float v0, float v1, float v2) {
            GL20.glUniform3f(this.getUniformLocation(name), v0, v1, v2);
        }

        public void setUniformMat4(String name, FloatBuffer matrix) {
            GL20.glUniformMatrix4fv(this.getUniformLocation(name), false, matrix);
        }
    }
}

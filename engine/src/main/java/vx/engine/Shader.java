package vx.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

/** Programa GLSL carregado do classpath ({@code /shaders/<nome>}). Uniforms inexistentes são ignorados. */
public final class Shader implements AutoCloseable {
    public final int id;
    private final String name;
    private final Map<String, Integer> locations = new HashMap<>();

    public Shader(String vertResource, String fragResource) {
        name = vertResource + "+" + fragResource;
        int vs = compile(GL_VERTEX_SHADER, vertResource);
        int fs = compile(GL_FRAGMENT_SHADER, fragResource);
        id = glCreateProgram();
        glAttachShader(id, vs);
        glAttachShader(id, fs);
        glLinkProgram(id);
        if (glGetProgrami(id, GL_LINK_STATUS) == 0)
            throw new IllegalStateException("link " + name + ":\n" + glGetProgramInfoLog(id));
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private static int compile(int type, String resource) {
        String src;
        try (InputStream in = Shader.class.getResourceAsStream("/shaders/" + resource)) {
            if (in == null) throw new IllegalStateException("shader não encontrado: " + resource);
            src = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        int s = glCreateShader(type);
        glShaderSource(s, src);
        glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == 0)
            throw new IllegalStateException("compilação " + resource + ":\n" + glGetShaderInfoLog(s));
        return s;
    }

    public Shader use() {
        glUseProgram(id);
        return this;
    }

    private int loc(String n) {
        // get antes de computeIfAbsent: a lambda captura o programa e era alocada a cada uniforme de cada desenho (JFR,
        // fechamento do QA); a posição continua guardada por nome
        Integer l = locations.get(n);
        if (l == null) {
            l = glGetUniformLocation(id, n);
            locations.put(n, l);
        }
        return l;
    }

    public Shader set(String n, int v) { glUniform1i(loc(n), v); return this; }
    public Shader set(String n, float v) { glUniform1f(loc(n), v); return this; }
    public Shader set(String n, boolean v) { glUniform1i(loc(n), v ? 1 : 0); return this; }
    public Shader set(String n, float x, float y) { glUniform2f(loc(n), x, y); return this; }
    public Shader set(String n, float x, float y, float z) { glUniform3f(loc(n), x, y, z); return this; }
    public Shader set(String n, float x, float y, float z, float w) { glUniform4f(loc(n), x, y, z, w); return this; }
    public Shader set(String n, Vector3f v) { glUniform3f(loc(n), v.x, v.y, v.z); return this; }

    public Shader set(String n, Matrix4f m) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            m.get(fb);
            glUniformMatrix4fv(loc(n), false, fb);
        }
        return this;
    }

    @Override
    public void close() { glDeleteProgram(id); }
}

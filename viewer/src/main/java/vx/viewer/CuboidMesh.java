package vx.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import vx.engine.Mesh;

/**
 * Malha de cuboides texturizados (direção C). Cada cuboide do JSON tem:
 * {@code from:[x,y,z], size:[w,h,d]} em texels do espaço do modelo, {@code faces} com o retângulo
 * {@code [u,v,w,h]} no atlas para px,nx,py,ny,pz,nz, e {@code flat:[r,g,b]} (cor média, usada no
 * modo sem texturas). Rotação opcional {@code rot:[rx,ry,rz]} em graus em torno de {@code origin}.
 */
public final class CuboidMesh {
    private CuboidMesh() { }

    private static final String[] FACE = {"px", "nx", "py", "ny", "pz", "nz"};
    private static final float[][] N = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    // 4 cantos por face (frações 0/1 de x,y,z), na ordem: sup-esq, sup-dir, inf-dir, inf-esq da textura
    // vista de fora da face.
    private static final float[][][] C = {
            {{1, 1, 1}, {1, 1, 0}, {1, 0, 0}, {1, 0, 1}},   // +x
            {{0, 1, 0}, {0, 1, 1}, {0, 0, 1}, {0, 0, 0}},   // -x
            {{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}},   // +y (topo: v cresce para +z)
            {{0, 0, 1}, {1, 0, 1}, {1, 0, 0}, {0, 0, 0}},   // -y
            {{0, 1, 1}, {1, 1, 1}, {1, 0, 1}, {0, 0, 1}},   // +z (frente)
            {{1, 1, 0}, {0, 1, 0}, {0, 0, 0}, {1, 0, 0}},   // -z (costas)
    };

    public static Mesh build(JsonArray cuboids, float texel, int atlasW, int atlasH) {
        float[] data = new float[cuboids.size() * 6 * 6 * Mesh.SCENE_STRIDE];
        int n = 0;
        org.joml.Matrix4f rot = new org.joml.Matrix4f();
        org.joml.Vector3f v = new org.joml.Vector3f(), nn = new org.joml.Vector3f();
        for (int ci = 0; ci < cuboids.size(); ci++) {
            JsonObject c = cuboids.get(ci).getAsJsonObject();
            JsonArray from = c.getAsJsonArray("from"), size = c.getAsJsonArray("size");
            float fx = from.get(0).getAsFloat(), fy = from.get(1).getAsFloat(), fz = from.get(2).getAsFloat();
            float sx = size.get(0).getAsFloat(), sy = size.get(1).getAsFloat(), sz = size.get(2).getAsFloat();
            JsonArray flat = c.getAsJsonArray("flat");
            rot.identity();
            if (c.has("rot")) {
                JsonArray r = c.getAsJsonArray("rot"), o = c.getAsJsonArray("origin");
                float ox = o.get(0).getAsFloat(), oy = o.get(1).getAsFloat(), oz = o.get(2).getAsFloat();
                rot.translate(ox, oy, oz)
                        .rotateZ((float) Math.toRadians(r.get(2).getAsFloat()))
                        .rotateY((float) Math.toRadians(r.get(1).getAsFloat()))
                        .rotateX((float) Math.toRadians(r.get(0).getAsFloat()))
                        .translate(-ox, -oy, -oz);
            }
            JsonObject faces = c.getAsJsonObject("faces");
            for (int f = 0; f < 6; f++) {
                if (!faces.has(FACE[f])) continue;
                JsonArray uv = faces.getAsJsonArray(FACE[f]);
                float u0 = uv.get(0).getAsFloat() / atlasW, v0 = uv.get(1).getAsFloat() / atlasH;
                float u1 = u0 + uv.get(2).getAsFloat() / atlasW, v1 = v0 + uv.get(3).getAsFloat() / atlasH;
                float[][] uvs = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
                rot.transformDirection(nn.set(N[f][0], N[f][1], N[f][2]));
                JsonArray fc = flat;
                if (c.has("flatFaces") && c.getAsJsonObject("flatFaces").has(FACE[f]))
                    fc = c.getAsJsonObject("flatFaces").getAsJsonArray(FACE[f]);
                float r = fc.get(0).getAsFloat() / 255f, g = fc.get(1).getAsFloat() / 255f, b = fc.get(2).getAsFloat() / 255f;
                for (int k : new int[]{0, 3, 2, 0, 2, 1}) {
                    float[] cc = C[f][k];
                    rot.transformPosition(v.set(fx + cc[0] * sx, fy + cc[1] * sy, fz + cc[2] * sz));
                    data[n++] = v.x * texel; data[n++] = v.y * texel; data[n++] = v.z * texel;
                    data[n++] = nn.x; data[n++] = nn.y; data[n++] = nn.z;
                    data[n++] = r; data[n++] = g; data[n++] = b;
                    data[n++] = 1f; data[n++] = 0f;
                    data[n++] = uvs[k][0]; data[n++] = uvs[k][1];
                }
            }
        }
        return new Mesh(data, n, Mesh.SCENE_LAYOUT);
    }
}

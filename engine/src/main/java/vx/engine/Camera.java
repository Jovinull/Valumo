package vx.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Câmera orbital em torno de um alvo (yaw/pitch em radianos, Y para cima). */
public final class Camera {
    public final Vector3f target = new Vector3f();
    public float yaw = 0.6f, pitch = 0.35f, distance = 8f;
    public float fovDeg = 40f, near = 0.05f, far = 400f;

    private final Matrix4f view = new Matrix4f(), proj = new Matrix4f(), viewProj = new Matrix4f();
    private final Vector3f position = new Vector3f();

    /** Último aspecto usado em {@link #update}. */
    public float aspect = 16f / 9f;

    public void update(float aspect) {
        this.aspect = aspect;
        pitch = Math.clamp(pitch, -1.45f, 1.45f);
        distance = Math.clamp(distance, 0.3f, 200f);
        float cp = (float) Math.cos(pitch);
        position.set(target.x + distance * cp * (float) Math.sin(yaw),
                target.y + distance * (float) Math.sin(pitch),
                target.z + distance * cp * (float) Math.cos(yaw));
        view.setLookAt(position, target, new Vector3f(0, 1, 0));
        proj.setPerspective((float) Math.toRadians(fovDeg), aspect, near, far);
        proj.mul(view, viewProj);
    }

    public Matrix4f view() { return view; }
    public Matrix4f proj() { return proj; }
    public Matrix4f viewProj() { return viewProj; }
    public Vector3f position() { return position; }
}

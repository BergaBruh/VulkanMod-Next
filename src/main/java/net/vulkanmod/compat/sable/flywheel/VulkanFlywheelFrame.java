package net.vulkanmod.compat.sable.flywheel;

import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

import java.nio.ByteBuffer;

final class VulkanFlywheelFrame {
    static final int SIZE = 272;

    static void write(ByteBuffer dst, Matrix4fc viewProjection, Vector3fc camera, float ticks, int fogShape,
                      int material, float[] fogColor, float fogStart, float fogEnd, boolean constantAmbient,
                      Vector3fc light0, Vector3fc light1, Matrix4fc pose, Matrix3fc normal) {
        // Convert the OpenGL clip-space Z from RenderContext exactly once. The viewport handles Y.
        new Matrix4f(viewProjection)
                .m02((viewProjection.m02() + viewProjection.m03()) * 0.5f)
                .m12((viewProjection.m12() + viewProjection.m13()) * 0.5f)
                .m22((viewProjection.m22() + viewProjection.m23()) * 0.5f)
                .m32((viewProjection.m32() + viewProjection.m33()) * 0.5f)
                .get(0, dst);
        vec3(dst, 64, camera);
        dst.putFloat(80, ticks).putFloat(84, ticks / 20).putInt(88, fogShape).putInt(92, material);
        for (int i = 0; i < 4; i++) dst.putFloat(96 + i * Float.BYTES, fogColor[i]);
        dst.putFloat(112, fogStart).putFloat(116, fogEnd).putInt(120, constantAmbient ? 1 : 0).putInt(124, 0);
        vec3(dst, 128, light0);
        vec3(dst, 144, light1);
        pose.get(160, dst);
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) dst.putFloat(224 + col * 16 + row * 4, normal.get(col, row));
            dst.putFloat(236 + col * 16, 0);
        }
    }

    private static void vec3(ByteBuffer dst, int offset, Vector3fc vec) {
        dst.putFloat(offset, vec.x()).putFloat(offset + 4, vec.y()).putFloat(offset + 8, vec.z()).putFloat(offset + 12, 0);
    }
}

package net.vulkanmod.compat.sable.flywheel;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class VulkanFlywheelFrameSmokeTest {
    public static void main(String[] args) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(VulkanFlywheelFrame.SIZE).order(ByteOrder.nativeOrder());
        var clip = new Matrix4f().m23(2).m32(3);
        VulkanFlywheelFrame.write(bytes, clip, new Vector3f(1, 2, 3), 25, 1, 42,
                new float[]{0, 1, 2, 3}, 4, 5, true, new Vector3f(6, 7, 8), new Vector3f(9, 10, 11),
                new Matrix4f().translation(12, 13, 14), new org.joml.Matrix3f().m21(15));
        if (bytes.position() != 0 || bytes.getFloat(40) != 1.5f || bytes.getFloat(56) != 2
                || bytes.getFloat(72) != 3 || bytes.getFloat(80) != 25 || bytes.getFloat(84) != 1.25f
                || bytes.getInt(88) != 1 || bytes.getInt(92) != 42 || bytes.getFloat(108) != 3
                || bytes.getFloat(116) != 5 || bytes.getInt(120) != 1 || bytes.getFloat(128) != 6
                || bytes.getFloat(152) != 11 || bytes.getFloat(156) != 0 || bytes.getFloat(208) != 12
                || bytes.getFloat(260) != 15 || bytes.getFloat(268) != 0) throw new AssertionError("Flywheel frame ABI");
    }
}

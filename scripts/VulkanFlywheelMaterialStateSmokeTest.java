package net.vulkanmod.compat.sable.flywheel;

import dev.engine_room.flywheel.api.material.DepthTest;
import dev.engine_room.flywheel.api.material.Transparency;
import net.vulkanmod.vulkan.shader.PipelineState.BlendState;
import net.vulkanmod.vulkan.shader.PipelineState.DepthState;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanFlywheelMaterialStateSmokeTest {
    public static void main(String[] args) {
        int off = VulkanFlywheelMaterialState.depthState(DepthTest.OFF, true);
        if (DepthState.depthTest(off) || DepthState.depthMask(off)) throw new AssertionError("depth off");
        int readOnly = VulkanFlywheelMaterialState.depthState(DepthTest.LEQUAL, false);
        if (!DepthState.depthTest(readOnly) || DepthState.depthMask(readOnly)
                || DepthState.decodeDepthFun(readOnly) != VK_COMPARE_OP_LESS_OR_EQUAL) throw new AssertionError("read-only depth");
        int writable = VulkanFlywheelMaterialState.depthState(DepthTest.GEQUAL, true);
        if (!DepthState.depthMask(writable) || DepthState.decodeDepthFun(writable) != VK_COMPARE_OP_GREATER_OR_EQUAL)
            throw new AssertionError("writable depth");
        if (BlendState.enable(VulkanFlywheelMaterialState.blendState(Transparency.OPAQUE))) throw new AssertionError("opaque blend");
        int alpha = VulkanFlywheelMaterialState.blendState(Transparency.TRANSLUCENT);
        if (!BlendState.enable(alpha) || BlendState.getSrcRgbFactor(alpha) != VK_BLEND_FACTOR_SRC_ALPHA
                || BlendState.getDstRgbFactor(alpha) != VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA
                || BlendState.getSrcAlphaFactor(alpha) != VK_BLEND_FACTOR_ONE
                || BlendState.getDstAlphaFactor(alpha) != VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA
                || alpha != VulkanFlywheelMaterialState.blendState(Transparency.ORDER_INDEPENDENT)) throw new AssertionError("alpha blend");
    }
}

package net.vulkanmod.compat.sable.flywheel;

import dev.engine_room.flywheel.api.material.DepthTest;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.shader.PipelineState;
import net.vulkanmod.vulkan.shader.PipelineState.AssemblyRasterState;
import net.vulkanmod.vulkan.shader.PipelineState.BlendInfo;
import net.vulkanmod.vulkan.shader.PipelineState.ColorMask;
import net.vulkanmod.vulkan.shader.PipelineState.DepthState;

import static org.lwjgl.vulkan.VK10.*;

public final class VulkanFlywheelMaterialState {
    private VulkanFlywheelMaterialState() {}

    public static PipelineState create(Material material, RenderPass renderPass) {
        int raster = AssemblyRasterState.encode(material.backfaceCulling() ? VK_CULL_MODE_BACK_BIT : VK_CULL_MODE_NONE,
                VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST, VK_POLYGON_MODE_FILL,
                Renderer.isRasterRowsBottomUp() ? VK_FRONT_FACE_CLOCKWISE : VK_FRONT_FACE_COUNTER_CLOCKWISE);
        boolean color = material.writeMask().color();
        return new PipelineState(raster, blendState(material.transparency()),
                depthState(material.depthTest(), material.writeMask().depth()), 0, 0,
                ColorMask.getColorMask(color, color, color, color), renderPass);
    }

    static int depthState(DepthTest depthTest, boolean writeDepth) {
        if (depthTest == DepthTest.OFF) return 0;
        int compare = switch (depthTest) {
            case OFF, NEVER -> VK_COMPARE_OP_NEVER;
            case LESS -> VK_COMPARE_OP_LESS;
            case EQUAL -> VK_COMPARE_OP_EQUAL;
            case LEQUAL -> VK_COMPARE_OP_LESS_OR_EQUAL;
            case GREATER -> VK_COMPARE_OP_GREATER;
            case NOTEQUAL -> VK_COMPARE_OP_NOT_EQUAL;
            case GEQUAL -> VK_COMPARE_OP_GREATER_OR_EQUAL;
            case ALWAYS -> VK_COMPARE_OP_ALWAYS;
        };
        return DepthState.DEPTH_TEST_BIT | (writeDepth ? DepthState.DEPTH_MASK_BIT : 0)
                | (compare << DepthState.DEPTH_FUN_OFFSET);
    }

    static int blendState(Transparency transparency) {
        return switch (transparency) {
            case OPAQUE -> 0;
            case ADDITIVE -> blend(VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE);
            case LIGHTNING -> blend(VK_BLEND_FACTOR_SRC_ALPHA, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_SRC_ALPHA, VK_BLEND_FACTOR_ONE);
            case GLINT -> blend(VK_BLEND_FACTOR_SRC_COLOR, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ZERO, VK_BLEND_FACTOR_ONE);
            case CRUMBLING -> blend(VK_BLEND_FACTOR_DST_COLOR, VK_BLEND_FACTOR_SRC_COLOR, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ZERO);
            // No OIT attachments or resolve pass: draw using ordinary alpha blending instead.
            case TRANSLUCENT, ORDER_INDEPENDENT -> blend(VK_BLEND_FACTOR_SRC_ALPHA, VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
                    VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
        };
    }

    private static int blend(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        return new BlendInfo(true, srcRgb, dstRgb, srcAlpha, dstAlpha, VK_BLEND_OP_ADD).createBlendState();
    }
}

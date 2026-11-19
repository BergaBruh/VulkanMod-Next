package net.vulkanmod.vulkan.shader;

import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.ManualUBO;
import net.vulkanmod.vulkan.shader.descriptor.UBO;

import java.util.List;

import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;

/** Compile with the project classpath and run net.vulkanmod.vulkan.shader.PipelineStorageBindingSmokeTest. */
public final class PipelineStorageBindingSmokeTest {
    public static void main(String[] args) {
        var builder = new Pipeline.Builder(null);
        builder.setUniforms(List.of(new UBO(0, VK_SHADER_STAGE_VERTEX_BIT, 4, List.of())),
                List.of(new ImageDescriptor(1, "sampler2D", "texture", 0)));
        builder.manualUBO = new ManualUBO(2, VK_SHADER_STAGE_VERTEX_BIT, 1);
        builder.validateStorageBufferBinding(); // Disabled by default.
        rejects(() -> builder.setStorageBufferBinding(-1));
        for (int binding : new int[]{0, 1, 2}) {
            builder.setStorageBufferBinding(binding);
            rejects(builder::validateStorageBufferBinding);
        }
        builder.setStorageBufferBinding(7); // No requirement to make binding numbers dense.
        builder.validateStorageBufferBinding();
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected an invalid storage buffer binding to be rejected");
    }
}

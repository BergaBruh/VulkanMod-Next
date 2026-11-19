package net.vulkanmod.compat.sable.flywheel;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.material.Material;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.ManualUBO;

import java.util.List;

import static net.vulkanmod.vulkan.shader.SPIRVUtils.ShaderKind.*;
import static org.lwjgl.vulkan.VK10.*;

record VulkanFlywheelPipeline(GraphicsPipeline graphics, ManualUBO frame) {
    static VulkanFlywheelPipeline create(VulkanFlywheelShaderSources sources, InstanceType<?> type, Material material) {
        String vertex = sources.vertex(type, material.shaders().vertexSource(), shader("api.vert"),
                shader("inputs.vert"), shader("main.vert"), "", 0, 1);
        String fragment = sources.fragment(material.shaders().fragmentSource(), material.fog().source(), material.cutout().source());
        var frame = new ManualUBO(0, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, VulkanFlywheelFrame.SIZE / 4);
        var builder = new Pipeline.Builder(DefaultVertexFormat.BLOCK, "flywheel/instancing");
        builder.setUniforms(List.of(frame), List.of(
                new ImageDescriptor(2, "sampler2D", "Material", 0),
                new ImageDescriptor(3, "sampler2D", "Overlay", 1),
                new ImageDescriptor(4, "sampler2D", "Lightmap", 2)));
        builder.setStorageBufferBinding(1);
        var vert = SPIRVUtils.compileShader("flywheel.vert", vertex, VERTEX_SHADER);
        try {
            var frag = SPIRVUtils.compileShader("flywheel.frag", fragment, FRAGMENT_SHADER);
            try {
                builder.setSPIRVs(vert, frag);
                return new VulkanFlywheelPipeline(builder.createGraphicsPipeline(), frame);
            } finally {
                frag.free();
            }
        } finally {
            vert.free();
        }
    }

    void draw(VulkanFlywheelMesh mesh, StorageBuffer instances, int count, ByteBuffer frameBytes,
              Material material, VulkanImage texture, VulkanImage overlay, VulkanImage lightmap) {
        if (count <= 0 || !frameBytes.isDirect() || frameBytes.remaining() != VulkanFlywheelFrame.SIZE) {
            throw new IllegalArgumentException("Invalid Flywheel draw inputs");
        }
        var renderer = Renderer.getInstance();
        if (!Renderer.isRecording() || renderer.getBoundRenderPass() == null) {
            throw new IllegalStateException("Flywheel draw requires an active Vulkan render pass");
        }
        var saved = VTextureSelector.captureBindings(0b111);
        float biasUnits = renderer.getAppliedDepthBiasUnits();
        float biasFactor = renderer.getAppliedDepthBiasFactor();
        try {
            Renderer.setDepthBias(material.polygonOffset() ? -10f : 0f, material.polygonOffset() ? -1f : 0f);
            VTextureSelector.bindTexture(0, texture);
            VTextureSelector.bindTexture(1, overlay);
            VTextureSelector.bindTexture(2, lightmap);
            renderer.bindGraphicsPipeline(graphics, VulkanFlywheelMaterialState.create(material, renderer.getBoundRenderPass()));
            frame.setSrc(MemoryUtil.memAddress(frameBytes), frameBytes.remaining());
            graphics.bindDescriptorSets(Renderer.getCommandBuffer(), Renderer.getCurrentFrame(), instances);
            Renderer.getDrawer().drawIndexed(mesh.vertices().getId(), 0, mesh.indices().getId(), 0,
                    mesh.indices().indexType.type, mesh.indexCount(), count, material.writeMask().color(),
                    material.writeMask().depth() && material.depthTest() != dev.engine_room.flywheel.api.material.DepthTest.OFF);
        } finally {
            frame.setSrc(0, 0);
            saved.restore();
            Renderer.setDepthBias(biasUnits, biasFactor);
        }
    }

    private static ResourceLocation shader(String file) {
        return ResourceLocation.fromNamespaceAndPath("vulkanmod", "instancing/" + file);
    }
}

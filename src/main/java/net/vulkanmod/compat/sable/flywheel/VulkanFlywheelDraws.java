package net.vulkanmod.compat.sable.flywheel;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.backend.engine.MaterialEncoder;
import dev.engine_room.flywheel.backend.mixin.LevelRendererAccessor;
import net.vulkanmod.Initializer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.gl.GlTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.StorageBuffer;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

final class VulkanFlywheelDraws {
    private final Map<Mesh, VulkanFlywheelMesh> meshes = new IdentityHashMap<>();
    private final Map<ShaderKey, VulkanFlywheelPipeline> pipelines = new HashMap<>();
    private VulkanFlywheelShaderSources sources;
    private boolean recordedDraw;

    boolean canDraw() {
        var renderer = Renderer.getInstance();
        return renderer != null && Renderer.isRecording() && renderer.getBoundRenderPass() != null;
    }

    void draw(RenderContext context, Vec3i origin, Matrix4fc pose, Matrix3fc normal,
              InstanceType<?> type, Model model, VulkanInstancer.PackedSnapshot batch) {
        if (batch.count() == 0) return;
        var client = Minecraft.getInstance();
        var gameRenderer = client.gameRenderer;
        var overlay = image(gameRenderer.overlayTexture().texture.getId());
        var lightmap = image(gameRenderer.lightTexture().lightTexture.getId());
        if (overlay == null || lightmap == null) return;
        if (sources == null) sources = new VulkanFlywheelShaderSources(client.getResourceManager());

        var position = context.camera().getPosition();
        var camera = new Vector3f((float) (position.x - origin.getX()), (float) (position.y - origin.getY()),
                (float) (position.z - origin.getZ()));
        var projection = new Matrix4f(context.viewProjection()).translate(-camera.x, -camera.y, -camera.z);
        var lights = RenderSystem.shaderLightDirections;
        // ponytail: immutable upload per batch; reuse per-frame buffers if profiling shows allocation pressure.
        var instances = new StorageBuffer(batch.bytes());
        try (var stack = MemoryStack.stackPush()) {
            var frame = stack.malloc(VulkanFlywheelFrame.SIZE);
            for (var configured : model.meshes()) {
                var material = configured.material();
                var texture = image(client.getTextureManager().getTexture(material.texture()).getId());
                if (texture == null || configured.mesh().indexCount() == 0) continue;
                var mesh = meshes.computeIfAbsent(configured.mesh(), VulkanFlywheelMesh::upload);
                var pipeline = pipelines.computeIfAbsent(ShaderKey.of(type, material),
                        key -> VulkanFlywheelPipeline.create(sources, key.type(), material));
                VulkanFlywheelFrame.write(frame, projection, camera,
                        ((LevelRendererAccessor) context.renderer()).flywheel$getTicks() + context.partialTick(),
                        RenderSystem.getShaderFogShape().ordinal(), MaterialEncoder.packProperties(material),
                        RenderSystem.getShaderFogColor(), RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(),
                        context.level().effects().constantAmbientLight(), lights[0], lights[1], pose, normal);
                pipeline.draw(mesh, instances, batch.count(), frame, material, texture, overlay, lightmap);
                if (!recordedDraw) {
                    recordedDraw = true;
                    Initializer.LOGGER.debug("Recorded first native Flywheel draw: {} instances, {} indices", batch.count(), mesh.indexCount());
                }
            }
        } finally {
            instances.freeBuffer();
        }
    }

    void close() {
        meshes.values().forEach(VulkanFlywheelMesh::close);
        meshes.clear();
        pipelines.values().forEach(pipeline -> pipeline.graphics().scheduleCleanUp());
        pipelines.clear();
        sources = null;
    }

    private static VulkanImage image(int id) {
        var texture = GlTexture.getTexture(id);
        return texture == null ? null : texture.getVulkanImage();
    }

    private record ShaderKey(InstanceType<?> type, ResourceLocation vertex, ResourceLocation fragment,
                             ResourceLocation fog, ResourceLocation cutout) {
        static ShaderKey of(InstanceType<?> type, Material material) {
            return new ShaderKey(type, material.shaders().vertexSource(), material.shaders().fragmentSource(),
                    material.fog().source(), material.cutout().source());
        }
    }
}

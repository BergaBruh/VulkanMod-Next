package net.vulkanmod.compat.sable.flywheel;

import dev.engine_room.flywheel.api.backend.Engine;
import dev.engine_room.flywheel.api.backend.RenderContext;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.task.RunnablePlan;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Camera;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class VulkanFlywheelEngine implements Engine, VisualizationContext, InstancerProvider {
    private final LevelAccessor level;
    private final VulkanFlywheelDraws draws = new VulkanFlywheelDraws();
    private final InstancerGroup instancers = new InstancerGroup();
    private final List<VulkanEmbedding> embeddings = new ArrayList<>();
    private final LongSet lightSections = new LongOpenHashSet();
    private final FlywheelFallbackController fallback = new FlywheelFallbackController();
    private Vec3i renderOrigin = Vec3i.ZERO;

    VulkanFlywheelEngine(LevelAccessor level) {
        this.level = level;
    }

    @Override
    public VisualizationContext createVisualizationContext() {
        return this;
    }

    @Override
    public Plan<RenderContext> createFramePlan() {
        return RunnablePlan.of(context -> prepareFrame());
    }

    @Override
    public Vec3i renderOrigin() {
        return renderOrigin;
    }

    @Override
    public boolean updateRenderOrigin(Camera camera) {
        Vec3i next = SectionPos.of(camera.getBlockPosition()).origin();
        if (next.equals(renderOrigin)) {
            return false;
        }
        renderOrigin = next;
        return true;
    }

    @Override
    public void lightSections(LongSet sections) {
        lightSections.clear();
        lightSections.addAll(sections);
    }

    @Override
    public void onLightUpdate(SectionPos sectionPos, LightLayer lightLayer) {
        lightSections.add(sectionPos.asLong());
    }

    @Override
    public void render(RenderContext context) {
        if (fallback.applyAtFrameBoundary()) {
            return;
        }
        if (!draws.canDraw()) return;
        instancers.draw(draws, context, renderOrigin, new org.joml.Matrix4f(), new org.joml.Matrix3f());
        synchronized (embeddings) {
            for (var embedding : embeddings) {
                synchronized (embedding.data) {
                    embedding.instancers.draw(draws, context, renderOrigin, embedding.data.pose(), embedding.data.normal());
                }
            }
        }
    }

    @Override
    public void renderCrumbling(RenderContext context, List<CrumblingBlock> crumblingBlocks) {
    }

    @Override
    public void delete() {
        instancers.close();
        draws.close();
        synchronized (embeddings) {
            List.copyOf(embeddings).forEach(VulkanEmbedding::delete);
        }
        lightSections.clear();
    }

    @Override
    public InstancerProvider instancerProvider() {
        return this;
    }

    @Override
    public VisualEmbedding createEmbedding(Vec3i renderOrigin) {
        VulkanEmbedding embedding = new VulkanEmbedding(renderOrigin);
        synchronized (embeddings) {
            embeddings.add(embedding);
        }
        return embedding;
    }

    @Override
    public <I extends Instance> Instancer<I> instancer(InstanceType<I> type, Model model, int bias) {
        return instancers.instancer(type, model, bias);
    }

    private void prepareFrame() {
        instancers.pack();
        synchronized (embeddings) {
            embeddings.forEach(embedding -> embedding.instancers.pack());
        }
    }

    private static final class InstancerGroup implements InstancerProvider {
        private final Map<Integer, Map<InstanceType<?>, Map<Model, VulkanInstancer<?>>>> batches = new java.util.TreeMap<>();
        private boolean closed;

        @Override
        @SuppressWarnings("unchecked")
        public synchronized <I extends Instance> Instancer<I> instancer(InstanceType<I> type, Model model, int bias) {
            if (closed) throw new IllegalStateException("Flywheel instancer group is closed");
            return (Instancer<I>) batches.computeIfAbsent(bias, ignored -> new IdentityHashMap<>())
                    .computeIfAbsent(type, ignored -> new IdentityHashMap<>())
                    .computeIfAbsent(model, ignored -> new VulkanInstancer<>(type, model));
        }

        synchronized void pack() {
            batches.values().forEach(types -> types.values().forEach(models ->
                    models.values().forEach(VulkanInstancer::writeDirtyInstances)));
        }

        synchronized void draw(VulkanFlywheelDraws draws, RenderContext context, Vec3i origin, Matrix4fc pose, Matrix3fc normal) {
            batches.values().forEach(types -> types.forEach((type, models) -> models.forEach((model, instancer) ->
                    draws.draw(context, origin, pose, normal, type, model, instancer.packedSnapshot()))));
        }

        synchronized void close() {
            closed = true;
            batches.values().forEach(types -> types.values().forEach(models ->
                    models.values().forEach(VulkanInstancer::close)));
            batches.clear();
        }
    }

    private final class VulkanEmbedding implements VisualEmbedding {
        private final VulkanEmbeddingData data = new VulkanEmbeddingData();
        private final Vec3i origin;
        private final InstancerGroup instancers = new InstancerGroup();

        private VulkanEmbedding(Vec3i origin) {
            this.origin = origin;
        }

        @Override
        public void transforms(Matrix4fc pose, Matrix3fc normal) {
            data.updateTransforms(pose, normal);
        }

        @Override
        public void delete() {
            synchronized (embeddings) {
                embeddings.remove(this);
                instancers.close();
            }
        }

        @Override
        public InstancerProvider instancerProvider() {
            return instancers;
        }

        @Override
        public Vec3i renderOrigin() {
            return origin;
        }

        @Override
        public VisualEmbedding createEmbedding(Vec3i renderOrigin) {
            return VulkanFlywheelEngine.this.createEmbedding(renderOrigin);
        }
    }
}

package net.vulkanmod.compat.sable.flywheel;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.backend.compile.component.InstanceStructComponent;
import dev.engine_room.flywheel.backend.compile.component.SsboInstanceComponent;
import dev.engine_room.flywheel.backend.engine.indirect.BufferBindings;
import dev.engine_room.flywheel.backend.glsl.ShaderSources;
import dev.engine_room.flywheel.backend.glsl.SourceComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Source assembly only. Recreate with the current resource manager on each reload. */
public final class VulkanFlywheelShaderSources {
    private static final String UPSTREAM_INSTANCE_LAYOUT = "layout(std430, binding = " + BufferBindings.INSTANCE + ")";
    private static final ResourceLocation FRAGMENT_API = ResourceLocation.parse("vulkanmod:instancing/api.frag");
    private static final ResourceLocation FRAGMENT_MAIN = ResourceLocation.parse("vulkanmod:instancing/main.frag");

    private final ShaderSources sources;

    public VulkanFlywheelShaderSources(ResourceManager resourceManager) {
        sources = new ShaderSources(resourceManager);
    }

    /** All resource IDs are relative to assets/&lt;namespace&gt;/flywheel/. */
    public String vertex(InstanceType<?> type, ResourceLocation materialVertex, ResourceLocation api,
                         ResourceLocation inputs, ResourceLocation main, String defines, int descriptorSet,
                         int instanceBinding) {
        if (descriptorSet < 0 || instanceBinding < 0) {
            throw new IllegalArgumentException("Instance descriptor set and binding must be non-negative");
        }

        var glsl = new StringBuilder("#version 450\n#define VERTEX_SHADER\n").append(defines).append('\n');
        Set<SourceComponent> seen = new HashSet<>();
        append(glsl, seen, sources.get(api));
        append(glsl, seen, new InstanceStructComponent(type));
        append(glsl, seen, sources.get(type.vertexShader()));
        append(glsl, seen, sources.get(materialVertex));
        append(glsl, seen, sources.get(inputs));

        String unpacker = new SsboInstanceComponent(type).source();
        if (!unpacker.contains(UPSTREAM_INSTANCE_LAYOUT)) {
            throw new IllegalStateException("Flywheel instance SSBO declaration changed");
        }
        glsl.append(unpacker.replace(UPSTREAM_INSTANCE_LAYOUT,
                "layout(std430, set = " + descriptorSet + ", binding = " + instanceBinding + ")")).append('\n');
        append(glsl, seen, sources.get(main));
        return glsl.toString();
    }

    /** Single-pass standalone fragment shader using the material's fog and alpha-cutout hooks. */
    public String fragment(ResourceLocation materialFragment, ResourceLocation fog, ResourceLocation cutout) {
        var glsl = new StringBuilder("#version 450\n#define FRAGMENT_SHADER\n");
        Set<SourceComponent> seen = new HashSet<>();
        for (var id : List.of(FRAGMENT_API, materialFragment, fog, cutout, FRAGMENT_MAIN)) {
            append(glsl, seen, sources.get(id));
        }
        return glsl.toString();
    }

    private static void append(StringBuilder glsl, Set<SourceComponent> seen, SourceComponent component) {
        if (seen.add(component)) {
            for (SourceComponent include : component.included()) {
                append(glsl, seen, include);
            }
            glsl.append('\n').append(component.source()).append('\n');
        }
    }
}

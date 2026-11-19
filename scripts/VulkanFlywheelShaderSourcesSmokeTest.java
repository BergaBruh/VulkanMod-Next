import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.layout.FloatRepr;
import dev.engine_room.flywheel.api.layout.IntegerRepr;
import dev.engine_room.flywheel.api.layout.LayoutBuilder;
import dev.engine_room.flywheel.impl.layout.LayoutBuilderImpl;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.vulkanmod.compat.sable.flywheel.VulkanFlywheelShaderSources;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.lwjgl.util.shaderc.Shaderc.*;

/**
 * Run with the project compile classpath and compiled main classes. Pass a scratch directory, then (optionally)
 * the Create 6.0.10 jar for production vertex compilation. Put LWJGL natives on the classpath.
 */
public final class VulkanFlywheelShaderSourcesSmokeTest {
    public static void main(String[] args) throws Exception {
        Path base = Path.of(args[0]).resolve("base");
        Path override = Path.of(args[0]).resolve("override");
        write(base, "shared.glsl", "const int baseValue = 0;");
        write(base, "nested.glsl", "const int nestedValue = 42;");
        write(override, "shared.glsl", "#include \"smoke:nested.glsl\"\nconst int packValue = nestedValue;");
        write(base, "api.vert", "#include \"smoke:shared.glsl\"\nconst int apiValue = packValue;");
        write(base, "instance.vert", "void instanceHook(FlwInstance instance) {}");
        write(base, "material.vert", "#include \"smoke:shared.glsl\"\nvoid materialHook() {}");
        write(base, "inputs.vert", "layout(location = 0) in vec3 position;");
        write(base, "main.vert", "void main() { gl_Position = vec4(position, 1.0); }");
        var layout = new LayoutBuilderImpl().scalarArray("packedValues", IntegerRepr.BYTE, 5).build();
        var type = new SimpleInstanceType<>((instanceType, handle) -> null, layout, (ptr, instance) -> {}, id("instance.vert"), id("instance.vert"));

        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack(base), pack(override)))) {
            String glsl = new VulkanFlywheelShaderSources(resources).vertex(type, id("material.vert"), id("api.vert"),
                    id("inputs.vert"), id("main.vert"), "#define FLW_EMBEDDED", 2, 9);
            int previous = -1;
            for (String component : List.of("#version 450", "#define VERTEX_SHADER", "#define FLW_EMBEDDED",
                    "const int nestedValue", "const int packValue", "const int apiValue", "struct FlwInstance",
                    "instanceHook", "materialHook",
                    "in vec3 position", "layout(std430, set = 2, binding = 9)", "uint _flw_instances[]", "* 2u;", "void main()")) {
                int at = glsl.indexOf(component, previous + 1);
                if (at < 0) {
                    throw new AssertionError("Missing or out of order: " + component + "\n" + glsl);
                }
                previous = at;
            }
            if (glsl.contains("#include") || glsl.contains("baseValue")
                    || glsl.indexOf("const int packValue") != glsl.lastIndexOf("const int packValue")) {
                throw new AssertionError("Pack override / include deduplication failed:\n" + glsl);
            }
        }
        if (args.length > 1) {
            compileProduction(Path.of(args[1]));
        }
    }

    private static void compileProduction(Path createJar) throws Exception {
        try (var flywheel = FileSystems.newFileSystem(Path.of("libs/flywheel-neoforge-1.21.1-1.0.6.jar"));
             var create = FileSystems.newFileSystem(createJar);
             var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                     List.of(pack(flywheel.getPath("/")), pack(create.getPath("/")), pack(Path.of("src/main/resources"))))) {
            // Create's AllInstanceTypes initializes the mod; mirror its 6.0.10 layouts without a game bootstrap.
            var rotating = createType("rotating", basicLayout(LayoutBuilder.create())
                    .vector("rotation", FloatRepr.FLOAT, 4).vector("pos", FloatRepr.FLOAT, 3)
                    .scalar("speed", FloatRepr.FLOAT).scalar("offset", FloatRepr.FLOAT)
                    .vector("axis", FloatRepr.NORMALIZED_BYTE, 3));
            var scrolling = createType("scrolling", scrollLayout(basicLayout(LayoutBuilder.create())
                    .vector("pos", FloatRepr.FLOAT, 3).vector("rotation", FloatRepr.FLOAT, 4)));
            var scrollingTransformed = createType("scrolling_transformed", scrollLayout(basicLayout(
                    LayoutBuilder.create().matrix("pose", FloatRepr.FLOAT, 4))));
            var fluid = createType("fluid", basicLayout(LayoutBuilder.create().matrix("pose", FloatRepr.FLOAT, 4))
                    .scalar("progress", FloatRepr.FLOAT).scalar("vScale", FloatRepr.FLOAT).scalar("v0", FloatRepr.FLOAT));
            if (rotating.layout().byteSize() != 52 || scrolling.layout().byteSize() != 72
                    || scrollingTransformed.layout().byteSize() != 108 || fluid.layout().byteSize() != 88) {
                throw new AssertionError("Create instance writer strides changed");
            }
            var assembler = new VulkanFlywheelShaderSources(resources);
            long compiler = shaderc_compiler_initialize();
            long options = shaderc_compile_options_initialize();
            try {
                shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_2);
                for (var type : List.of(InstanceTypes.TRANSFORMED, InstanceTypes.ORIENTED, InstanceTypes.POSED,
                        rotating, scrolling, scrollingTransformed, fluid)) {
                    String name = type.vertexShader().toString();
                    String glsl = assembler.vertex(type, ResourceLocation.parse("flywheel:material/default.vert"),
                            ResourceLocation.parse("vulkanmod:instancing/api.vert"), ResourceLocation.parse("vulkanmod:instancing/inputs.vert"),
                            ResourceLocation.parse("vulkanmod:instancing/main.vert"), "", 0, 1);
                    compile(compiler, options, glsl, shaderc_glsl_vertex_shader, name);
                }
                for (var hooks : List.of(List.of("default", "linear", "one_tenth"),
                        List.of("default", "none", "off"), List.of("lines", "linear_fade", "half"))) {
                    String glsl = assembler.fragment(ResourceLocation.parse("flywheel:material/" + hooks.get(0) + ".frag"),
                            ResourceLocation.parse("flywheel:fog/" + hooks.get(1) + ".glsl"),
                            ResourceLocation.parse("flywheel:cutout/" + hooks.get(2) + ".glsl"));
                    compile(compiler, options, glsl, shaderc_glsl_fragment_shader, String.join("-", hooks) + ".frag");
                }
            } finally {
                shaderc_compile_options_release(options);
                shaderc_compiler_release(compiler);
            }
        }
    }

    private static void compile(long compiler, long options, String glsl, int stage, String name) {
        long result = shaderc_compile_into_spv(compiler, glsl, stage, name, "main", options);
        if (result == 0) {
            throw new AssertionError("shaderc returned no result for " + name);
        }
        try {
            if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
                throw new AssertionError(name + ": " + shaderc_result_get_error_message(result));
            }
        } finally {
            shaderc_result_release(result);
        }
        System.out.println("Compiled " + name);
    }

    private static LayoutBuilder basicLayout(LayoutBuilder builder) {
        return builder.vector("color", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4)
                .vector("light", IntegerRepr.SHORT, 2).vector("overlay", IntegerRepr.SHORT, 2);
    }

    private static LayoutBuilder scrollLayout(LayoutBuilder builder) {
        return builder.vector("speed", FloatRepr.FLOAT, 2).vector("diff", FloatRepr.FLOAT, 2)
                .vector("scale", FloatRepr.FLOAT, 2).vector("offset", FloatRepr.FLOAT, 2);
    }

    private static InstanceType<?> createType(String name, LayoutBuilder builder) {
        var vertex = ResourceLocation.fromNamespaceAndPath("create", "instance/" + name + ".vert");
        return new SimpleInstanceType<>((type, handle) -> null, builder.build(), (ptr, instance) -> {}, vertex, vertex);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("smoke", path);
    }

    private static PathPackResources pack(Path root) {
        var name = root.toUri().toString();
        return new PathPackResources(new PackLocationInfo(name, Component.literal(name), PackSource.DEFAULT, Optional.empty()), root);
    }

    private static void write(Path pack, String name, String shader) throws Exception {
        var file = pack.resolve("assets/smoke/flywheel").resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, shader);
    }
}

package net.vulkanmod.compat.sable.flywheel;

import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.layout.IntegerRepr;
import dev.engine_room.flywheel.impl.layout.LayoutBuilderImpl;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteOrder;

/** Standalone smoke test: compile with the project classpath, run with LWJGL natives and -ea. */
public final class VulkanFlywheelInstancerSmokeTest {
    public static void main(String[] args) {
        var layout = new LayoutBuilderImpl().scalarArray("packedValues", IntegerRepr.BYTE, 5).build();
        var shader = ResourceLocation.withDefaultNamespace("unused.vert");
        var type = new SimpleInstanceType<TestInstance>(TestInstance::new, layout, (ptr, instance) -> {
            MemoryUtil.memPutInt(ptr, instance.value);
            MemoryUtil.memPutByte(ptr + 4, (byte) 99);
        }, shader, shader);

        try (var source = new VulkanInstancer<>(type, null); var target = new VulkanInstancer<>(type, null)) {
            var a = source.createInstance();
            var b = source.createInstance();
            var c = source.createInstance();
            a.value = 11;
            b.value = 22;
            c.value = 33;
            var handle = b.handle();
            check(source.packedSnapshot(), 11, 22, 33);
            assert source.writeDirtyInstances().equals(new DirtyRangeTracker.Range(0, 24));
            assert source.writeDirtyInstances() == null;
            b.setVisible(false);
            check(source.packedSnapshot(), 11, 33);
            b.setVisible(true);
            check(source.packedSnapshot(), 11, 22, 33);

            target.stealInstance(b);
            target.stealInstance(b);
            assert b.handle() == handle;
            check(source.packedSnapshot(), 11, 33);
            check(target.packedSnapshot(), 22);
            b.value = 222;
            b.setChanged();
            check(target.packedSnapshot(), 222);

            b.setVisible(false);
            assert !handle.isVisible();
            source.stealInstance(b);
            check(target.packedSnapshot());
            check(source.packedSnapshot(), 11, 33);
            b.value = 2222;
            b.setChanged();
            b.setVisible(true);
            assert handle.isVisible();
            check(source.packedSnapshot(), 11, 33, 2222);

            a.delete();
            a.setVisible(true);
            target.stealInstance(a);
            assert !a.handle().isVisible();
            check(target.packedSnapshot());
            check(source.packedSnapshot(), 33, 2222);
            b.delete();
            b.delete();
            check(source.packedSnapshot(), 33);
            source.close();
            assert !c.handle().isVisible();
        }
    }

    private static void check(VulkanInstancer.PackedSnapshot snapshot, int... values) {
        var bytes = snapshot.bytes();
        assert snapshot.count() == values.length;
        assert snapshot.stride() == 8;
        assert bytes.isReadOnly() && bytes.isDirect() && bytes.order() == ByteOrder.nativeOrder();
        assert bytes.remaining() == 8 * values.length;
        for (int i = 0; i < values.length; i++) {
            int offset = i * 8;
            assert bytes.getInt(offset) == values[i];
            assert bytes.get(offset + 4) == 99;
            for (int padding = 5; padding < 8; padding++) {
                assert bytes.get(offset + padding) == 0;
            }
        }
    }

    private static final class TestInstance extends AbstractInstance {
        int value;

        TestInstance(InstanceType<TestInstance> type, InstanceHandle handle) {
            super(type, handle);
        }
    }
}

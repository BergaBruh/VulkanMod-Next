package net.vulkanmod.compat.sable.flywheel;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.model.Model;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VulkanInstancer<I extends Instance> implements Instancer<I>, AutoCloseable {
    // ponytail: serialize ownership and frame packing across instancers; split locks if parallel visuals contend.
    private static final Object LOCK = new Object();

    private final InstanceType<I> type;
    private final Model model;
    private final int recordBytes;
    private final int stride;
    private final Map<Handle, I> instances = new LinkedHashMap<>();
    private final DirtyRangeTracker dirtyRanges = new DirtyRangeTracker();
    private ByteBuffer shadowStorage;
    private int visibleCount;
    private boolean repack;

    VulkanInstancer(InstanceType<I> type, Model model) {
        this.type = type;
        this.model = model;
        this.recordBytes = type.layout().byteSize();
        this.stride = Math.addExact(recordBytes, 3) & ~3;
        this.shadowStorage = MemoryUtil.memAlloc(1);
    }

    @Override
    public I createInstance() {
        synchronized (LOCK) {
            ensureOpen();
            Handle handle = new Handle(this);
            I instance = type.create(handle);
            if (handle.owner == this) {
                instances.put(handle, instance);
                if (handle.visible) {
                    visibleCount++;
                }
            }
            return instance;
        }
    }

    @Override
    public void stealInstance(I instance) {
        if (instance == null || instance.type() != type || !(instance.handle() instanceof Handle handle)) {
            return;
        }
        synchronized (LOCK) {
            ensureOpen();
            VulkanInstancer<?> previous = handle.owner;
            if (previous == null || previous == this || previous.instances.get(handle) != instance) {
                return;
            }
            previous.instances.remove(handle);
            instances.put(handle, instance);
            handle.owner = this;
            handle.changed = true;
            if (handle.visible) {
                previous.visibleCount--;
                previous.repack = true;
                visibleCount++;
            }
        }
    }

    /** Borrowed native storage; copy/upload before this instancer packs again or closes. */
    PackedSnapshot packedSnapshot() {
        synchronized (LOCK) {
            packVisible();
            return new PackedSnapshot(packedBytes(), visibleCount, stride);
        }
    }

    DirtyRangeTracker.Range writeDirtyInstances() {
        synchronized (LOCK) {
            packVisible();
            var range = dirtyRanges.take().orElse(null);
            int end = visibleCount * stride;
            return range == null || range.startInclusive() >= end ? null
                    : new DirtyRangeTracker.Range(range.startInclusive(), Math.min(range.endExclusive(), end));
        }
    }

    List<I> visibleInstances() {
        synchronized (LOCK) {
            return instances.entrySet().stream().filter(entry -> entry.getKey().visible).map(Map.Entry::getValue).toList();
        }
    }

    Model model() {
        return model;
    }

    ByteBuffer shadowStorage() {
        return packedSnapshot().bytes();
    }

    private ByteBuffer packedBytes() {
        return shadowStorage.asReadOnlyBuffer().limit(visibleCount * stride).order(ByteOrder.nativeOrder());
    }

    private void packVisible() {
        ensureOpen();
        int required = Math.multiplyExact(visibleCount, stride);
        if (shadowStorage.capacity() < required) {
            int growth = (int) Math.min(Integer.MAX_VALUE, (long) shadowStorage.capacity() * 2);
            shadowStorage = MemoryUtil.memRealloc(shadowStorage, Math.max(required, growth));
        }

        var writer = type.writer();
        long address = MemoryUtil.memAddress(shadowStorage);
        int offset = 0;
        for (var entry : instances.entrySet()) {
            Handle handle = entry.getKey();
            if (!handle.visible) {
                continue;
            }
            if (repack || handle.changed) {
                writer.write(address + offset, entry.getValue());
                if (stride != recordBytes) {
                    MemoryUtil.memSet(address + offset + recordBytes, 0, stride - recordBytes);
                }
                handle.changed = false;
                dirtyRanges.mark(offset, offset + stride);
            }
            offset += stride;
        }
        repack = false;
    }

    private void ensureOpen() {
        if (shadowStorage == null) {
            throw new IllegalStateException("Instancer is closed");
        }
    }

    @Override
    public void close() {
        synchronized (LOCK) {
            if (shadowStorage != null) {
                MemoryUtil.memFree(shadowStorage);
                shadowStorage = null;
                for (Handle handle : instances.keySet()) {
                    handle.owner = null;
                }
                instances.clear();
                visibleCount = 0;
                dirtyRanges.take();
            }
        }
    }

    record PackedSnapshot(ByteBuffer bytes, int count, int stride) {
    }

    private static final class Handle implements InstanceHandle {
        private VulkanInstancer<?> owner;
        private boolean changed = true;
        private boolean visible = true;

        private Handle(VulkanInstancer<?> owner) {
            this.owner = owner;
        }

        @Override
        public void setChanged() {
            synchronized (LOCK) {
                changed = true;
            }
        }

        @Override
        public void setDeleted() {
            synchronized (LOCK) {
                if (owner != null && owner.instances.remove(this) != null && visible) {
                    owner.visibleCount--;
                    owner.repack = true;
                }
                owner = null;
            }
        }

        @Override
        public void setVisible(boolean visible) {
            synchronized (LOCK) {
                if (owner != null && this.visible != visible) {
                    if (owner.instances.containsKey(this)) {
                        owner.visibleCount += visible ? 1 : -1;
                        owner.repack = true;
                    }
                    this.visible = visible;
                }
            }
        }

        @Override
        public boolean isVisible() {
            synchronized (LOCK) {
                return owner != null && visible;
            }
        }
    }
}

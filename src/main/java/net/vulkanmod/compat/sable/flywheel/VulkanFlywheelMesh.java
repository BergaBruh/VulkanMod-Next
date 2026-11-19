package net.vulkanmod.compat.sable.flywheel;

import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.lib.vertex.NoOverlayVertexView;
import net.vulkanmod.vulkan.memory.IndexBuffer;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.VertexBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

record VulkanFlywheelMesh(VertexBuffer vertices, IndexBuffer indices, int indexCount) implements AutoCloseable {
    static VulkanFlywheelMesh upload(Mesh mesh) {
        int count = mesh.vertexCount();
        int indexCount = mesh.indexCount();
        if (count <= 0 || indexCount <= 0) throw new IllegalArgumentException("Empty Flywheel mesh");
        int vertexBytes = Math.multiplyExact(count, 32);
        int indexBytes = Math.multiplyExact(indexCount, Integer.BYTES);
        ByteBuffer bytes = MemoryUtil.memCalloc(vertexBytes);
        VertexBuffer vertices = null;
        IndexBuffer indices = null;
        try {
            packVertices(mesh, bytes, count);
            vertices = new VertexBuffer(vertexBytes);
            vertices.copyToVertexBuffer(32, count, bytes);
            indices = new IndexBuffer(indexBytes, MemoryTypes.HOST_MEM, IndexBuffer.IndexType.INT);
            mesh.indexSequence().fill(indices.getMappedAddress(), indexCount);
            return new VulkanFlywheelMesh(vertices, indices, indexCount);
        } catch (RuntimeException | Error e) {
            if (vertices != null) vertices.freeBuffer();
            if (indices != null) indices.freeBuffer();
            throw e;
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    // Flywheel's no-overlay format matches BLOCK except for the final padding byte.
    static void packVertices(Mesh mesh, ByteBuffer destination, int count) {
        long stride = NoOverlayVertexView.STRIDE;
        long packed = MemoryUtil.nmemCallocChecked(count, stride);
        try {
            var view = new NoOverlayVertexView();
            view.ptr(packed);
            view.vertexCount(count);
            mesh.write(view);
            long target = MemoryUtil.memAddress(destination);
            for (int i = 0; i < count; i++) {
                MemoryUtil.memCopy(packed + i * stride, target + i * 32L, stride);
                MemoryUtil.memPutByte(target + i * 32L + stride, (byte) 0);
            }
        } finally {
            MemoryUtil.nmemFree(packed);
        }
    }

    @Override
    public void close() {
        vertices.freeBuffer();
        indices.freeBuffer();
    }
}

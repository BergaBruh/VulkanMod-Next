#include "flywheel:internal/fog_distance.glsl"
#include "flywheel:internal/packed_material.glsl"

// Standalone meshes use zero base vertex; firstInstance selects the SSBO slot.
void main() {
    _flw_unpackMaterialProperties(_flw_packedMaterialProperties, flw_material);
    flw_vertexId = uint(gl_VertexIndex);
    _flw_layoutVertex();
    flw_instanceVertex(_flw_unpackInstance(uint(gl_InstanceIndex)));
    flw_materialVertex();
    flw_vertexPos = _vkm_embeddingPose * flw_vertexPos;
    flw_vertexNormal = normalize(_vkm_embeddingNormal * flw_vertexNormal);
    flw_distance = fogDistance(flw_vertexPos.xyz, _flw_cameraPos.xyz, flw_fogShape);
    gl_Position = flw_viewProjection * flw_vertexPos;
}

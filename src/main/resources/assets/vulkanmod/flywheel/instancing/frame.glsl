layout(std140, set = 0, binding = 0) uniform VulkanFlywheelFrame {
    mat4 flw_viewProjection;
    vec4 _flw_cameraPos;
    float flw_renderTicks;
    float flw_renderSeconds;
    int flw_fogShape;
    uint _flw_packedMaterialProperties;
    vec4 flw_fogColor;
    vec2 flw_fogRange;
    uint flw_constantAmbientLight;
    vec4 _flw_light0Direction;
    vec4 _flw_light1Direction;
    mat4 _vkm_embeddingPose;
    mat3 _vkm_embeddingNormal;
};

#define flw_cameraPos (_flw_cameraPos.xyz)
#define flw_light0Direction (_flw_light0Direction.xyz)
#define flw_light1Direction (_flw_light1Direction.xyz)

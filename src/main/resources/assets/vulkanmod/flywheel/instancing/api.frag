#include "flywheel:internal/material.glsl"
#include "flywheel:internal/api_impl.glsl"
#include "vulkanmod:instancing/frame.glsl"

layout(location = 0) in vec4 flw_vertexPos;
layout(location = 1) in vec4 flw_vertexColor;
layout(location = 2) in vec2 flw_vertexTexCoord;
layout(location = 3) flat in ivec2 flw_vertexOverlay;
layout(location = 4) in vec2 flw_vertexLight;
layout(location = 5) in vec3 flw_vertexNormal;
layout(location = 6) in float flw_distance;

layout(set = 0, binding = 2) uniform sampler2D flw_diffuseTex;
layout(set = 0, binding = 3) uniform sampler2D flw_overlayTex;
layout(set = 0, binding = 4) uniform sampler2D flw_lightTex;

FlwMaterial flw_material;
vec4 flw_sampleColor;
bool flw_fragDiffuse;
vec4 flw_fragColor;
ivec2 flw_fragOverlay;
vec2 flw_fragLight;

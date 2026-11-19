#include "flywheel:internal/packed_material.glsl"
#include "flywheel:internal/diffuse.glsl"

layout(location = 0) out vec4 _flw_outputColor;

// Standalone pass: packed mesh/instance light only; no world light LUT or OIT.
void main() {
    _flw_unpackMaterialProperties(_flw_packedMaterialProperties, flw_material);
    flw_sampleColor = texture(flw_diffuseTex, flw_vertexTexCoord);
    flw_fragColor = flw_vertexColor * flw_sampleColor;
    flw_fragOverlay = flw_vertexOverlay;
    flw_fragLight = flw_vertexLight;
    flw_materialFragment();

    vec4 color = flw_fragColor;
    if (flw_discardPredicate(color)) {
        discard;
    }

    if (flw_material.cardinalLightingMode == FLW_MAT_CARDINAL_LIGHTING_MODE_ENTITY) {
        color.rgb *= diffuseFromLightDirections(flw_vertexNormal);
    } else if (flw_material.cardinalLightingMode == FLW_MAT_CARDINAL_LIGHTING_MODE_CHUNK) {
        color.rgb *= flw_constantAmbientLight == 1u ? diffuseNether(flw_vertexNormal) : diffuse(flw_vertexNormal);
    }
    if (flw_material.useOverlay) {
        vec4 overlay = texelFetch(flw_overlayTex, flw_fragOverlay, 0);
        color.rgb = mix(overlay.rgb, color.rgb, overlay.a);
    }
    if (flw_material.useLight) {
        color *= texture(flw_lightTex, clamp(flw_fragLight, 0.5 / 16.0, 15.5 / 16.0));
    }
    _flw_outputColor = flw_fogFilter(color);
}

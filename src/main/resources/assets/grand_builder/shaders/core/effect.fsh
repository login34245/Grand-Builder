#version 330

#moj_import <minecraft:fog.glsl>

in vec4 vertexColor;
in float sphericalDistance;
in float cylindricalDistance;
out vec4 fragColor;

void main() {
    if (vertexColor.a < 0.003) discard;
#ifdef EMISSIVE
    float fog = total_fog_value(sphericalDistance, cylindricalDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    float alpha = vertexColor.a * (1.0 - fog);
    fragColor = vec4(vertexColor.rgb * alpha, alpha);
#else
    fragColor = apply_fog(vertexColor, sphericalDistance, cylindricalDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
#endif
}

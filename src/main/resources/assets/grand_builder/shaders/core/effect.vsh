#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:fog.glsl>

in vec3 Position;
in vec4 Color;
out vec4 vertexColor;
out float sphericalDistance;
out float cylindricalDistance;

void main() {
    vec4 viewPosition = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPosition;
    vertexColor = Color * ColorModulator;
    sphericalDistance = fog_spherical_distance(Position);
    cylindricalDistance = fog_cylindrical_distance(Position);
}

#version 330
#extension GL_ARB_separate_shader_objects : require

// 천상 오로라: 지평 좌표 반구 그물 → 조각 셰이더에 시선 방향 (북 = −Z, 동 = +X, 위 = +Y)

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(location = 0) out vec3 dir;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    dir = Position;
}

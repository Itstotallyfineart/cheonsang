#version 330
#extension GL_ARB_separate_shader_objects : require

// 천상 해·달 공용 정점 셰이더: 단위 사각형(-1~1) → p = 사각형 안 위치 (x = 오른쪽, y = 위)

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(location = 0) out vec2 p;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    p = vec2(UV0.x * 2.0 - 1.0, 1.0 - UV0.y * 2.0);
}

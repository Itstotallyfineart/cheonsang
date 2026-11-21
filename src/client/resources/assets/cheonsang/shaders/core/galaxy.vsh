#version 330
#extension GL_ARB_separate_shader_objects : require

// 천상 1.10.0 은하 관측 스케치: 은하마다 사각형 하나 (긴 축 × 짧은 축 방향), UV → 사각형 안 위치 −1~1
// UV0.x = 사진 판 칸 번호 × 2 + 위치(0~1), 칸 없으면 −2 + 위치 (dev1b)
// Color: r = 짧은/긴 축 비 · g = 형태 번호 × 16 + 무늬 씨앗 · b = 진하기(적분 등급 문턱 × 세기) · a = 보이는 밝기 문턱 (GalaxySketch.kt)

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec2 uv;
layout(location = 1) out vec4 prm;
layout(location = 2) out float tile;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    tile = floor(UV0.x / 2.0);
    uv = vec2(UV0.x - tile * 2.0, UV0.y) * 2.0 - 1.0;
    prm = Color;
}

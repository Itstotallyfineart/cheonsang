#version 330
#extension GL_ARB_separate_shader_objects : require

// 천상 은하 스케치: 은하마다 사각형 하나, UV → 사각형 내 위치 −1~1
// UV0.x: 사진판 칸 번호 ×2 + 위치, 칸 없으면 −2 + 위치
// Color (GalaxySketch.kt): r=축비, g=형태×16+무늬씨앗, b=진하기, a=밝기 문턱

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

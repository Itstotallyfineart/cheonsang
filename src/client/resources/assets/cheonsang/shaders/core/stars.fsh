#version 330
#extension GL_ARB_separate_shader_objects : require

// 별 점을 그림(star.png) 대신 원형 가우스로 직접 계산 — 최근접 샘플링이면 하늘이 돌 때 별이 픽셀 경계를 넘으며 깜빡임.
// corner: 사각형 안 위치 0~1. 사각형 반 폭 = 별 반지름 r 의 2배 → 가운데에서 r 만큼 떨어지면 |p| = 0.5

#include <minecraft:dynamictransforms.glsl>

// 파이프라인이 Sampler0 을 묶으므로 남겨 둠 (값은 늘 1)
uniform sampler2D Sampler0;

layout(location = 0) in vec2 corner;
layout(location = 1) in vec4 starColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 p = corner * 2.0 - 1.0;
    float d2 = dot(p, p) * 4.0; // (거리 / r)^2
    float keep = step(-1.0, texture(Sampler0, vec2(0.5)).a);
    float a = exp(-1.5 * d2) * starColor.a * keep;
    if (a <= 0.002) {
        discard;
    }
    fragColor = vec4(starColor.rgb, min(a, 1.0));
}

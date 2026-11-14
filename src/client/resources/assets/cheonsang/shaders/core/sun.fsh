#version 330
#extension GL_ARB_separate_shader_objects : require

// 천상 해: 원판을 계산으로 그림 → 확대해도 가장자리 매끈. 주연감광 + 빛번짐. 더하기 합성.
// ColorModulator.rgb = 해 색 (지평선 근처 붉게), a = 밝기
// TextureMat[0] = (주연감광 계수, 빛무리 세기, 빛무리 감쇠, 사각형 반지름 ÷ 해 반지름)
// TextureMat[1] = (가장자리 부드러움 — 해 반지름 비율, 넓은 빛번짐 세기, 넓은 빛번짐 감쇠, 0)

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 p;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 prm = TextureMat[0];
    vec4 prm2 = TextureMat[1];
    float q = length(p);
    float r = q * prm.w;
    float aa = max(fwidth(r), 1e-4) + prm2.x;
    float disc = 1.0 - smoothstep(1.0 - aa, 1.0 + aa * 0.5, r);
    float mu = sqrt(max(0.0, 1.0 - min(r * r, 1.0)));
    float limb = 1.0 - prm.x * (1.0 - mu);
    float out_ = max(r - 1.0, 0.0);
    float halo = (prm.y * exp(-out_ * prm.z) + prm2.y * exp(-out_ * prm2.z)) * (1.0 - disc);
    halo *= 1.0 - smoothstep(0.7, 1.0, q);
    float k = (disc * limb + halo) * ColorModulator.a * texture(Sampler0, vec2(0.5)).a;
    if (k <= 0.001) {
        discard;
    }
    fragColor = vec4(ColorModulator.rgb, min(k, 1.0));
}

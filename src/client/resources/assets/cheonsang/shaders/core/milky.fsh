#version 330
#extension GL_ARB_separate_shader_objects : require

// 천상 1.2.2 은하수 (화질 A안): 바이큐빅 보간 (Mitchell–Netravali B = C = 1/3) + 디더링.
// 확대하면 텍셀이 화면 픽셀보다 커져 네모·계단이 보이던 것을 4×4 텍셀 곡면 보간으로 매끈하게.
// Mitchell 필터는 약간 부드럽게 하는 성질이 있어 노이즈는 살짝만 줄고 별 알갱이·암흑 구름은 남음.
// 디더링: 화면 픽셀마다 ±0.5/255 안팎의 잡음 → 어두운 그러데이션의 띠(밴딩) 깨기.
// TextureMat[0].x = 1 이면 한 채널(R8) 흑백 그림 (현실 은하수), 0 이면 RGBA (장노출 은하수)
// 가로(적경)는 이어지고(반복), 세로(적위)는 끝에서 멈춤
// 1.6.1 조각: TextureMat[1].x = 1 이면 가로도 끝에서 멈춤 (조각에 2px 여백이 있음), TextureMat[2] = (배율 u, 배율 v, 이동 u, 이동 v) — 전체 uv → 조각 uv (0 이면 그대로)

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

float mitchell(float x) {
    x = abs(x);
    if (x < 1.0) return (7.0 * x * x * x - 12.0 * x * x + 16.0 / 3.0) / 6.0;
    if (x < 2.0) return (-7.0 / 3.0 * x * x * x + 12.0 * x * x - 20.0 * x + 32.0 / 3.0) / 6.0;
    return 0.0;
}

vec3 texel(ivec2 p, ivec2 size, bool gray, bool clampX) {
    p.x = clampX ? clamp(p.x, 0, size.x - 1) : ((p.x % size.x) + size.x) % size.x;
    p.y = clamp(p.y, 0, size.y - 1);
    vec4 c = texelFetch(Sampler0, p, 0);
    return gray ? vec3(c.r) : c.rgb;
}

float hash(vec2 p) {
    p = fract(p * vec2(443.897, 441.423));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

void main() {
    bool gray = TextureMat[0].x > 0.5;
    ivec2 size = textureSize(Sampler0, 0);
    bool clampX = TextureMat[1].x > 0.5;
    vec2 uv = TextureMat[2].x > 0.0 ? texCoord0 * TextureMat[2].xy + TextureMat[2].zw : texCoord0;
    vec2 st = uv * vec2(size) - 0.5;
    vec2 base = floor(st);
    vec2 f = st - base;
    ivec2 b = ivec2(base);
    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int j = -1; j <= 2; j++) {
        float wy = mitchell(float(j) - f.y);
        for (int i = -1; i <= 2; i++) {
            float w = mitchell(float(i) - f.x) * wy;
            sum += texel(b + ivec2(i, j), size, gray, clampX) * w;
            wsum += w;
        }
    }
    vec3 c = max(sum / wsum, vec3(0.0));
    float n = hash(gl_FragCoord.xy) + hash(gl_FragCoord.yx + 7.31) - 1.0;
    vec3 col = c * ColorModulator.rgb;
    // 은하수 빛이 없는 곳(검은 하늘)에는 잡음을 안 넣음
    float lum = dot(col, vec3(0.333));
    col = max(col + vec3(n / 255.0) * clamp(lum * 64.0, 0.0, 1.0), vec3(0.0));
    fragColor = vec4(col, ColorModulator.a);
}

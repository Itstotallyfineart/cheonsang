#version 330
#extension GL_ARB_separate_shader_objects : require

// 은하수: 바이큐빅 보간 (Mitchell–Netravali B=C=1/3, 4×4 텍셀) + 디더링 (화면 픽셀마다 ±0.5/255, 밴딩 방지).
// TextureMat[0].x=1 이면 흑백(R8, 현실 은하수), 0 이면 RGBA(장노출). 가로(적경) 반복, 세로(적위) 클램프.
// 조각 텍스처: TextureMat[1].x=1 이면 가로도 클램프, TextureMat[2]=(배율u,배율v,이동u,이동v) 로 전체 uv → 조각 uv 변환 (0 이면 그대로).

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

#version 330
#extension GL_ARB_separate_shader_objects : require

// 천상 달: 달 지도 1장(정거원통, 가운데 경도 0°)을 공에 입히고 해 방향으로 실시간 위상.
// p.x = 하늘 서쪽(오른쪽), p.y = 천구 북쪽, z = 지구 쪽. 두 번 그림:
//   가림막 (TextureMat[2].z = 0, 반투명): 원판을 ColorModulator 색·진하기로 덮음 — 밤은 검게, 일식 때는 하늘색으로 해를 가림. 낮엔 안 그림
//   달빛 (TextureMat[2].z = 1, 더하기): 빛 받는 쪽만 더함 → 낮에도 하늘보다 밝은 옅은 흰빛
// 밝기는 실제 달처럼 가장자리까지 고름 (Lommel-Seeliger: 해 쪽 cos ÷ (해 쪽 cos + 보는 쪽 cos))
// ColorModulator = 가림막: (색 r, g, b, 진하기) · 달빛: (1, 1, 1, 진하기)
// TextureMat[0] = (해 방향 x, y, z, 달 밝기 — 낮에는 옅게), TextureMat[1] = (월식 색 r, g, b, 지구빛)
// TextureMat[2] = (0, 무늬 명암 — 맨눈 낮춤, 그리기 종류, 색 지도 화성)
// TextureMat[3] = (칭동 경도 l, 칭동 위도 b, 자전축 위치각 P, 0) — 라디안 (Meeus 53장 광학 칭동)

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 p;

layout(location = 0) out vec4 fragColor;

void main() {
    float r2 = dot(p, p);
    float r = sqrt(r2);
    float aa = max(fwidth(r), 1e-4);
    float cover = 1.0 - smoothstep(1.0 - aa, 1.0 + aa, r);
    if (cover <= 0.001) {
        discard;
    }
    if (TextureMat[2].z < 0.5) {
        fragColor = vec4(ColorModulator.rgb, cover * ColorModulator.a);
        return;
    }
    float z = sqrt(max(0.0, 1.0 - r2));
    // 칭동 보정: 하늘 좌표를 달 자전축 기준(P 만큼 회전)으로 돌린 뒤, 지구 쪽 점이 (l, b)가 되도록 돌려 위도·경도를 얻음
    float cP = cos(TextureMat[3].z), sP = sin(TextureMat[3].z);
    float xm = cP * p.x + sP * p.y;
    float ym = -sP * p.x + cP * p.y;
    float cb = cos(TextureMat[3].y), sb = sin(TextureMat[3].y);
    float cl = cos(TextureMat[3].x), sl = sin(TextureMat[3].x);
    float bx = cb * z - sb * ym;
    float bz = sb * z + cb * ym;
    float X = cl * bx - sl * xm;
    float Y = sl * bx + cl * xm;
    float lat = asin(clamp(bz, -1.0, 1.0));
    float lon = atan(Y, X);
    vec2 uv = vec2(0.5 + lon / 6.2831853, 0.5 - lat / 3.1415927);
    // 거의 무채색에 살짝 따뜻하게 (지도 원본의 푸른기 제거)
    vec3 tex = texture(Sampler0, uv).rgb;
    float g = dot(tex, vec3(0.299, 0.587, 0.114));
    g = mix(0.55, g, TextureMat[2].y);
    vec3 albedo = g * vec3(1.0, 0.98, 0.95) * 1.6;
    // TextureMat[2].w = 1 이면 색 지도 그대로 (화성)
    if (TextureMat[2].w > 0.5) {
        albedo = tex * 1.5;
    }
    vec3 s = normalize(TextureMat[0].xyz);
    float d = dot(vec3(p, z), s);
    float mu0 = max(d, 0.0);
    float lit = clamp((d + 0.01) / 0.04, 0.0, 1.0) * 2.0 * mu0 / (mu0 + z + 1e-3);
    vec3 light = albedo * (lit * TextureMat[1].rgb + TextureMat[1].a) * TextureMat[0].w;
    // 밝은 부분이 하얗게 날아가지 않게 부드럽게 눌러 줌 — 어두운 쪽은 거의 그대로, 최대 0.85
    light = 0.85 * (vec3(1.0) - exp(-light / 0.85));
    fragColor = vec4(light, cover * ColorModulator.a);
}

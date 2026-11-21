#version 330
#extension GL_ARB_separate_shader_objects : require

// 천상 달 (0.1.8): 달 지도 1장(정거원통, 가운데 경도 0°)을 공에 입히고 해 방향으로 실시간 위상.
// p.x = 하늘 서쪽(오른쪽), p.y = 천구 북쪽, z = 지구 쪽. 두 번 그림:
//   가림막 (TextureMat[2].z = 0, 반투명): 원판을 ColorModulator 색·진하기로 덮음 — 밤은 검게, 일식 때는 하늘색으로 해를 가림. 낮엔 안 그림
//   달빛 (TextureMat[2].z = 1, 더하기): 빛 받는 쪽만 더함 → 낮에도 하늘보다 밝은 옅은 흰빛
// 밝기는 실제 달처럼 가장자리까지 고름 (Lommel-Seeliger: 해 쪽 cos ÷ (해 쪽 cos + 보는 쪽 cos))
// ColorModulator = 가림막: (색 r, g, b, 진하기) · 달빛: (1, 1, 1, 진하기)
// TextureMat[0] = (해 방향 x, y, z, 달 밝기 — 낮에는 옅게), TextureMat[1] = (월식 색 r, g, b, 지구빛)
// TextureMat[2] = (0, 무늬 명암 — 맨눈 낮춤, 그리기 종류, 색 지도 1.2.4 화성)
// TextureMat[3] = (칭동 경도 l, 칭동 위도 b, 자전축 위치각 P, 0) — 라디안 (1.2.3, Meeus 53장 광학 칭동)
// 2.0.2-dev1 월식: TextureMat[2].x = 반영 반지름 (달 반지름 단위, 0 = 그림자 없음) 이면 달빛 패스 ColorModulator.rgb = (그림자 중심 x, y, 본영 반지름)
// 2.0.2-dev2: TextureMat[3].w = 본영이 달 지름을 덮은 비율 (부분일 땐 본영 쪽을 거의 어둡게, 개기에 가까울수록 눈이 적응하듯 붉은빛을 올림)

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
    // 1.2.3 칭동: TextureMat[3] = (지구 쪽 셀레노 경도 l, 위도 b, 자전축 위치각 P — 라디안)
    // 하늘(서쪽, 북쪽) → 달 축 기준 (P 만큼 돌림) → 지구 쪽 점이 (l, b) 가 되게 돌림 → 위도·경도
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
    vec4 t4 = texture(Sampler0, uv);
    vec3 tex = t4.rgb;
    // 2.0.2-dev3: TextureMat[2].w = 2 → 고도 지도를 담은 달 지도 (R 밝기 · G 동쪽 기울기 · B 북쪽 기울기 · A 고도)
    bool relief = TextureMat[2].w > 1.5;
    float g = relief ? t4.r : dot(tex, vec3(0.299, 0.587, 0.114));
    g = mix(0.55, g, TextureMat[2].y);
    vec3 albedo = g * vec3(1.0, 0.98, 0.95) * 1.6;
    // 1.2.4: TextureMat[2].w = 1 이면 색 지도 그대로 (화성)
    if (TextureMat[2].w > 0.5 && !relief) {
        albedo = tex * 1.5;
    }
    vec3 s = normalize(TextureMat[0].xyz);
    float d = dot(vec3(p, z), s);
    float mu0 = max(d, 0.0);
    if (relief) {
        // 해 방향을 달 몸 좌표(위와 같은 회전)로
        float sxm = cP * s.x + sP * s.y, sym = -sP * s.x + cP * s.y;
        float sbx = cb * s.z - sb * sym, sbz = sb * s.z + cb * sym;
        vec3 S = vec3(cl * sbx - sl * sxm, sl * sbx + cl * sxm, sbz);
        vec3 B = vec3(X, Y, bz);
        vec3 east = normalize(vec3(-Y, X, 0.0) + vec3(1e-5, 0.0, 0.0));
        vec3 north = cross(B, east);
        // 표면 기울기로 노멀 (지도 해상도가 크레이터 벽보다 거칠어 2배 강조)
        vec3 n = normalize(B - 2.0 * ((t4.g - 0.5) * 2.0 * east + (t4.b - 0.5) * 2.0 * north));
        mu0 = max(dot(n, S), 0.0);
        // 명암 경계 근처: 해 쪽으로 고도 지도를 따라가며 가리는 산·크레이터 벽이 있으면 그림자 (거리 0.5~115 km, 달 곡률 포함)
        float sinE = dot(B, S);
        if (sinE > -0.02 && sinE < 0.3) {
            vec2 dir = normalize(vec2(dot(S, east), dot(S, north)) + vec2(1e-5));
            float tanE = sinE / sqrt(max(1.0 - sinE * sinE, 1e-4));
            float h0 = t4.a * 21.0 - 10.0;
            float occl = 0.0;
            for (int i = 1; i <= 14; i++) {
                float dk = float(i * i) * 0.59;
                vec2 duv = vec2(dir.x * dk / (1737.4 * max(cos(lat), 0.05)) / 6.2831853, -dir.y * dk / 1737.4 / 3.1415927);
                float hs = textureLod(Sampler0, uv + duv, 0.0).a * 21.0 - 10.0 - dk * dk / (2.0 * 1737.4);
                occl = max(occl, clamp((hs - h0 - dk * tanE) / 0.25, 0.0, 1.0));
            }
            mu0 *= 1.0 - occl;
        }
    }
    float lit = clamp((d + 0.01) / 0.04, 0.0, 1.0) * 2.0 * mu0 / (mu0 + z + 1e-3);
    vec3 ecl = TextureMat[1].rgb;
    if (TextureMat[2].x > 0.0) {
        float ur = ColorModulator.z, pr = TextureMat[2].x;
        float dd = length(p - ColorModulator.xy);
        float pen = clamp((pr - dd) / max(pr - ur, 1e-3), 0.0, 1.0);
        vec3 e = vec3(1.0) - pen * pen * vec3(0.55, 0.62, 0.7);
        float um = 1.0 - smoothstep(ur - 0.16, ur + 0.06, dd);
        float deep = clamp((ur - dd) / ur, 0.0, 1.0);
        vec3 red = mix(vec3(0.6, 0.24, 0.1), vec3(0.3, 0.07, 0.035), sqrt(deep));
        float adapt = mix(0.07, 1.0, smoothstep(0.75, 1.0, TextureMat[3].w));
        ecl *= mix(e, red * adapt, um);
    }
    vec3 light = albedo * (lit * ecl + TextureMat[1].a) * TextureMat[0].w;
    // 1.3.0: 밝은 부분이 하얗게 날아가지 않게 부드럽게 눌러 줌 — 어두운 쪽은 거의 그대로, 최대 0.85 (TBD)
    light = 0.85 * (vec3(1.0) - exp(-light / 0.85));
    fragColor = vec4(light, cover * ColorModulator.a);
}

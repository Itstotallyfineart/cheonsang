#version 330
#extension GL_ARB_separate_shader_objects : require

// 천상 오로라 (1.9.1-dev6, 5판): 하늘 픽셀마다 시선을 따라 오로라 층(높이 80~320 km, 지구 곡률 포함) 속을 걸으며
// 두께 5 km 커튼 3장(동서로 긴 막, 극 쪽 거리 N km 에서 잡음으로 굽이침)의 빛 + 넓고 옅은 빛 장막을 모음.
// - 커튼마다 그 커튼이 있을 수 있는 구간(극 쪽 거리 ± 굽이침 ± 두께)만 잘라 64걸음 고르게 → 걸음 위치가 픽셀 사이에서 매끄럽게 이어져 물결 무늬 없음 (dev4)
// - 한 걸음이 덮는 폭보다 가는 무늬(빛기둥·두께·아래 끝)는 그 폭만큼 평균 → 흔들기 없이 반짝임·입자 없음 (dev2·dev3)
// - 막을 옆면으로 길게 지나면 막 속 길이가 정면의 4배 근처에서 더 늘지 않게 → 꽉 찬 초록 기둥 없음 (dev5, 11-14 03:01 지율님 "밀도가 너무 높은건")
// - 밝기는 잘라내지 않고 부드럽게 눌러 담고, 아주 밝은 곳만 살짝 흰빛
// - 넓고 옅은 빛 장막(띠 ± 320 km)이 맨 뒤 커튼 아래까지 깔려 "산 그림자" 같은 빈 경계를 덜어 줌
// ColorModulator.a = 전체 밝기 (세기·망원경 배율 반영)
// TextureMat[0] = (시각 초 0~100000, 세기 0~1, 오로라 띠까지 극 쪽 거리 km — 음수면 적도 쪽, 반구 부호 +1 북 / −1 남)

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec3 dir;

layout(location = 0) out vec4 fragColor;

const float R_EARTH = 6371.0;
const int STEPS = 64;        // 커튼 한 장당 걸음
const int VSTEPS = 12;       // 빛 장막 걸음 (넓고 매끈해서 적어도 됨)
const float W = 5.0;         // 커튼 두께 (km, 가우스 반폭)
const float WIN = 2.6 * W;   // 커튼 영향 범위 (km, 이 밖은 밝기 0.1% 미만)
const float WARP = 125.0;    // 굽이침 최대 폭 (km)
const float H_LO = 80.0;     // 아래 끝 밑 옅은 빛 자락까지
const float H_HI = 320.0;
const float SIDE = 4.0;      // 옆면 밝기 상한 (정면의 배수)
const float FRONT = W * 1.7725;   // 커튼을 정면으로 지날 때 막 속 길이 = W·√π

// 정수 해시 (sin 해시는 큰 값에서 그래픽카드마다 정밀도가 달라 무늬가 깨짐)
float hash1(float n) {
    uint x = uint(int(n));
    x ^= x >> 16u;
    x *= 0x7feb352du;
    x ^= x >> 15u;
    x *= 0x846ca68bu;
    x ^= x >> 16u;
    return float(x >> 8u) * (1.0 / 16777216.0);
}

// 값 잡음: 정수 칸마다 난수, 사이는 부드럽게
float vnoise(float x) {
    float i = floor(x);
    float f = x - i;
    float u = f * f * (3.0 - 2.0 * f);
    return mix(hash1(i), hash1(i + 1.0), u);
}

// 무늬 크기 L 보다 넓은 폭 f 를 한 번에 보면 무늬는 평균(0.5)으로
float lod(float f, float L) {
    return smoothstep(0.35 * L, 1.2 * L, f);
}

// 커튼이 극 쪽으로 얼마나 굽이치는지 (km) — 수백 km 굽이 + 백여 km 잔물결, 천천히 흐름
float warp(float x, float t, float s) {
    return 200.0 * (vnoise(x / 700.0 + t * 0.006 + s * 7.1) - 0.5)
         + 40.0 * (vnoise(x / 170.0 - t * 0.017 + s * 3.7) - 0.5);
}

// 높이 h 에 닿는 시선 거리 (km): s·up + s²·(1−up²)/2R = h
float distAt(float h, float up, float a) {
    return (-up + sqrt(up * up + 4.0 * a * h)) / (2.0 * a);
}

void main() {
    vec4 prm = TextureMat[0];
    float t = prm.x;
    float st = prm.y;
    float D = prm.z;
    float hemi = prm.w;

    vec3 d = normalize(dir);
    float up = d.y;
    if (up < -0.05) discard;
    // 남반구는 극 쪽이 남 → 180° 돌려서 같은 식
    float e = d.x * hemi;    // 동
    float nn = -d.z * hemi;  // 극 쪽
    float a = max(1.0 - up * up, 1e-6) / (2.0 * R_EARTH);
    float s0 = distAt(H_LO, up, a);
    float s1 = min(distAt(H_HI, up, a), 3500.0);
    if (s1 <= s0) discard;
    // 픽셀 하나가 덮는 각 (라디안)
    float pxAng = max(length(fwidth(d)), 1e-5);
    vec3 green = vec3(0.20, 1.0, 0.45);
    vec3 redc = vec3(0.95, 0.25, 0.30);

    vec3 acc = vec3(0.0);
    for (int i = 0; i < 3; i++) {
        float s = float(i);
        float Ni = D + (s - 1.0) * 150.0 + 60.0 * sin(t * 0.009 + s * 2.3);
        // 이 커튼이 있을 수 있는 극 쪽 거리 범위 → 시선 거리 범위 (n = sd·nn 이라 바로 나옴)
        float nlo = Ni - WARP - WIN;
        float nhi = Ni + WARP + WIN;
        float sa = s0;
        float sb = s1;
        if (abs(nn) > 1e-4) {
            float u0 = nlo / nn;
            float u1 = nhi / nn;
            sa = max(s0, min(u0, u1));
            sb = min(s1, max(u0, u1));
        } else if (nlo > 0.0 || nhi < 0.0) {
            continue;
        }
        if (sb <= sa) continue;
        float ds = (sb - sa) / float(STEPS);
        // 한 걸음이 극 쪽으로 덮는 폭 → 커튼 두께가 그보다 얇으면 넓혀서 평균 (빛 총량 같게)
        float fn = ds * (abs(nn) + 0.5 * abs(e));
        float weff = sqrt(W * W + 0.64 * fn * fn);
        float thin = W / weff;

        vec3 ci = vec3(0.0);
        float G = 0.0;   // 빛나는 막 속을 지난 길이 (걸음 단위)
        for (int k = 0; k < STEPS; k++) {
            float sd = sa + (float(k) + 0.5) * ds;
            float x = sd * e;
            float dn = sd * nn - (Ni + warp(x, t, s));
            float q = dn / weff;
            if (q * q > 7.0) continue;   // 커튼에서 멀면 건너뜀
            float g = exp(-q * q) * thin;
            float h = sd * up + sd * sd * a;
            // 이 걸음이 덮는 동서 폭(걸음 또는 픽셀 중 큰 쪽)·높이 폭
            float fx = max(ds * abs(e), sd * pxAng);
            float fh = ds * abs(up + 2.0 * a * sd);

            // 세로: 아래 끝 100~112 km, 위로 길게 옅어짐 (빛기둥마다 길이 다름)
            float hb = 100.0 + 12.0 * mix(vnoise(x / 60.0 + t * 0.02 + s * 13.0), 0.5, lod(fx, 60.0));
            float len = (70.0 + 130.0 * mix(vnoise(x / 18.0 + t * 0.06 + s * 17.0), 0.5, lod(fx, 18.0))) * (0.6 + 0.5 * st);
            float y = h - hb;
            float bw = sqrt(9.0 + 0.64 * fh * fh);   // 아래 끝 부드러움 3 km (걸음이 성기면 그만큼)
            // 아래 끝 밑: 또렷한 끝 + 옅은 빛 자락
            float vert = y < 0.0 ? 0.85 * exp(-(y / bw) * (y / bw)) + 0.15 * exp(y / 14.0)
                                 : exp(-y / (len * 0.42)) * (1.0 - smoothstep(len * 0.7, len * 1.15, y));
            if (vert < 1e-3) continue;
            G += g * vert;

            // 큰 덩어리: 밝은 마디와 끊긴 틈 (수십 초에 걸쳐 바뀜)
            float patch = smoothstep(0.22, 0.62, mix(vnoise(x / 380.0 + t * 0.012 + s * 11.0), 0.5, lod(fx, 380.0)));
            if (patch <= 0.0) continue;
            // 빛기둥: 굵기 2~8 km, 막을 따라 미끄러지며 몇 초마다 깜빡 — 폭이 넓으면 평균
            float n7 = mix(vnoise(x / 7.0 + t * 0.35 + s * 5.0), 0.5, lod(fx, 7.0));
            float n3 = mix(vnoise(x / 2.6 - t * 0.8 + s * 9.0), 0.5, lod(fx, 2.6));
            float nr = 0.6 * n7 + 0.4 * n3;
            float ray = 0.2 + 1.7 * nr * nr * nr;
            ray = mix(ray, 0.55, lod(fx, 5.0) * 0.6);
            ray *= 0.65 + 0.35 * mix(vnoise(x / 30.0 + t * 0.4 + s), 0.5, lod(fx, 30.0));

            vec3 col = mix(green, redc, smoothstep(135.0, 250.0, h));
            // 세면 아래 끝 바로 밑 분홍 (N2)
            if (y < 0.0) col = mix(col, vec3(0.95, 0.35, 0.75), smoothstep(0.5, 1.0, st) * 0.5);

            ci += col * (g * patch * ray * vert);
        }
        // 옆면 상한: 막 속 길이가 정면의 SIDE 배 근처에서 더 늘지 않게 (정면·조금 비스듬한 곳은 거의 그대로)
        float Gn = G * ds / FRONT;
        float lim = inversesqrt(1.0 + (Gn / SIDE) * (Gn / SIDE));
        acc += ci * (ds * lim * (1.0 - s * 0.2) / FRONT);
    }

    // 넓고 옅은 빛 장막: 띠 ± 320 km, 높이 90~250 km, 정면으로 지날 때 밝기 0.10 (커튼 정면 = 1)
    {
        float dv = (s1 - s0) / float(VSTEPS);
        vec3 veil = vec3(0.0);
        float Gv = 0.0;
        for (int k = 0; k < VSTEPS; k++) {
            float sd = s0 + (float(k) + 0.5) * dv;
            float n = sd * nn;
            float h = sd * up + sd * sd * a;
            float gv = exp(-((n - D) / 320.0) * ((n - D) / 320.0));
            float hv = smoothstep(86.0, 112.0, h) * exp(-max(h - 112.0, 0.0) / 70.0);
            float pv = 0.55 + 0.45 * vnoise(sd * e / 600.0 + t * 0.004 + 3.0);
            veil += mix(green, redc, smoothstep(160.0, 260.0, h)) * (gv * hv * pv);
            Gv += gv * hv;
        }
        // 정면으로 지날 때 장막 속 길이 ≈ 320·√π = 567 km. 동서로 길게 지나도 그 3배 근처에서 멈춤
        Gv *= dv / 567.0;
        acc += veil * (dv / 567.0 * 0.10 * (0.5 + 0.5 * st) * inversesqrt(1.0 + Gv * Gv / 9.0));
    }

    // 지평선 바로 위는 대기 때문에 옅게
    acc *= smoothstep(-0.02, 0.06, up) * 0.9;
    float m = max(max(acc.r, acc.g), acc.b);
    float k = m * ColorModulator.a * texture(Sampler0, vec2(0.5)).a;
    if (k <= 0.002) {
        discard;
    }
    // 잘라내지 않고 부드럽게 눌러 담기 (k 작을 땐 거의 그대로), 아주 밝은 곳만 살짝 흰빛
    vec3 c = acc / m;
    c = mix(c, vec3(1.0), smoothstep(0.9, 3.0, k) * 0.3);
    fragColor = vec4(c, 1.0 - exp(-1.4 * k));
}

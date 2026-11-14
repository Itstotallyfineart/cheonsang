#version 330
#extension GL_ARB_separate_shader_objects : require

// 천상 은하 관측 스케치: 빛무리 대신. 사진 판(DSS2, Sampler0 R8, 칸 128px, v = 1 + log10(I/I최대)/3)이 있으면 실제 빛 분포,
// 없으면 형태(타원·렌즈형·나선 a~d·불규칙, 막대)별 표면 밝기 모양에서 문턱보다 밝은 부분만 회색으로.
// 모양 식은 GalaxySketch.kt 의 profile() 과 같음 (바꿀 때 둘 다). 위치 단위 = D25 긴 반지름 (반폭 1.3), ColorModulator.a = 하늘 어두움

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 uv;
layout(location = 1) in vec4 prm;
layout(location = 2) in float tile;

layout(location = 0) out vec4 fragColor;

const float EXT = 1.3;

float hash2(vec2 c) {
    uint x = uint(int(c.x)) * 0x8da6b343u ^ uint(int(c.y)) * 0xd8163841u;
    x ^= x >> 16u;
    x *= 0x7feb352du;
    x ^= x >> 15u;
    return float(x >> 8u) * (1.0 / 16777216.0);
}

float vnoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1, 0)), u.x), mix(hash2(i + vec2(0, 1)), hash2(i + vec2(1, 1)), u.x), u.y);
}

void main() {
    float q = max(prm.r, 0.05);
    float g = floor(prm.g * 255.0 + 0.5);
    float code = floor(g / 16.0);
    float seed = g - code * 16.0;
    float type = mod(code, 8.0);
    bool barred = code >= 8.0;
    float qq = max(q, 0.15);
    float x = uv.x * EXT;
    float y = uv.y * EXT * qq;

    // 원반 면 위치 (기울기 풀기)
    float xd = x;
    float yd = y / q;
    float rd = sqrt(xd * xd + yd * yd);
    // 문턱: a = −log10(문턱)/2.5, 하늘이 밝으면 올라감
    float thr = pow(10.0, -2.5 * prm.a) * pow(max(ColorModulator.a, 0.02), -0.8);
    // 상이 크게 보일수록 구조가 더 잘 보이므로 문턱을 낮춤 (큰 구경·고배율)
    float scrPx = 2.0 / max(length(fwidth(uv)), 1e-6);
    float big = 0.3 * smoothstep(150.0, 900.0, scrPx);
    float I;
    if (tile > -0.5) {
        // 사진 판 칸 안 위치 → 판 좌표 (반 픽셀 안쪽). 정수 칸 번호로 보간이 옆 칸 경계를 읽는 것(M17 줄무늬) 방지
        float ti = floor(tile + 0.5);
        vec2 cr = vec2(mod(ti, 32.0), floor(ti / 32.0));
        vec2 l01 = clamp(uv * 0.5 + 0.5, 0.5 / 128.0, 1.0 - 0.5 / 128.0);
        vec2 tc = (cr + l01) / vec2(32.0, 16.0);
        float v = texture(Sampler0, tc).r;
        float Ic = v > 0.002 ? pow(10.0, 3.0 * (v - 1.0)) : 0.0;
        // 매끈한 빛 (반지름 칸의 6%, 8방향) — 구조가 안 보일 때·화면에서 작을 때
        float Is = 0.0;
        float rr = 0.06 / 32.0;
        for (int k = 0; k < 8; k++) {
            float an = float(k) * 0.785398;
            float vs = texture(Sampler0, tc + vec2(cos(an), sin(an) * 2.0) * rr).r;
            Is += vs > 0.002 ? pow(10.0, 3.0 * (vs - 1.0)) : 0.0;
        }
        Is = 0.5 * Is / 8.0 + 0.5 * Ic;
        // 구조는 빛이 문턱보다 꽤 밝은 곳에서만, 화면에서 칸 픽셀이 화면 픽셀보다 작으면 매끈한 쪽
        float sv = smoothstep(0.35 - big, 0.9 - big, log(max(Is, 1e-6) / thr) / 2.302585);
        float fw = length(fwidth(l01)) * 128.0;
        sv *= 1.0 - smoothstep(0.7, 2.0, fw);
        I = mix(Is, Ic, sv);
    } else if (type < 0.5) {
        // 타원은하: 세르식 n=2, 유효 반지름 0.22
        I = exp(-3.67 * sqrt(rd / 0.22)) * (1.0 - smoothstep(0.9, 1.25, rd));
    } else {
        // 형태별 값: 팽대부 세기, 원반 세기(팽대부보다 2~3등 어두움), 팽대부 반지름, 나선팔 세기, 감김각(도)
        float fb = type < 4.5 ? 1.0 : type < 5.5 ? 0.6 : 0.4;
        float fd = type < 1.5 ? 0.25 : type < 2.5 ? 0.15 : type < 3.5 ? 0.2 : type < 4.5 ? 0.3 : type < 5.5 ? 0.5 : 1.0;
        float re = type < 1.5 ? 0.10 : type < 2.5 ? 0.08 : type < 3.5 ? 0.07 : type < 4.5 ? 0.06 : type < 5.5 ? 0.05 : 0.12;
        float armA = type < 1.5 ? 0.0 : type < 2.5 ? 0.2 : type < 3.5 ? 0.35 : type < 4.5 ? 0.45 : type < 5.5 ? 0.35 : 0.0;
        float pitch = type < 2.5 ? 10.0 : type < 3.5 ? 15.0 : type < 4.5 ? 22.0 : 28.0;
        float h = type < 5.5 ? 0.22 : 0.3;
        float qb = max(q, 0.6);
        float rb = sqrt(x * x + (y / qb) * (y / qb));
        float bul = exp(-3.67 * sqrt(rb / re));
        float Ids = exp(-rd / h);      // 매끈한 원반 (구조 없음)
        float Id = Ids;
        if (armA > 0.0) {
            float th = atan(yd, xd);
            float s = 2.0 * (th - log(rd + 0.03) / tan(radians(pitch))) + seed * 0.3927;
            float c = 0.5 + 0.5 * cos(s);
            float arm = 1.0 + armA * (3.2 * c * c * c - 1.0);
            arm *= 0.8 + 0.4 * vnoise2(vec2(xd, yd) * 6.0 + seed * 3.0);   // 팔 속 덩어리 (HII)
            Id *= mix(1.0, arm, smoothstep(0.05, 0.2, rd));
        }
        if (type > 5.5) Id *= 0.5 + 0.9 * vnoise2(vec2(xd, yd) * 4.0 + seed * 7.0);   // 불규칙: 덩어리
        if (barred) {
            float ang = seed * 0.7;
            float xb = xd * cos(ang) + yd * sin(ang);
            float yb = -xd * sin(ang) + yd * cos(ang);
            Id += 1.5 * exp(-(xb / 0.22) * (xb / 0.22) - (yb / 0.06) * (yb / 0.06));
        }
        // 원반 바깥 끝 (D25 근처에서 사라짐)
        float trunc = 1.0 - smoothstep(0.9, 1.25, rd);
        float Is = fb * bul + fd * Ids * trunc;
        // 구조(나선팔·막대·덩어리)는 빛이 문턱보다 꽤 밝은 곳에서만 보임, 그 외엔 매끈한 빛만
        float sv = smoothstep(0.35 - big, 0.9 - big, log(max(Is, 1e-6) / thr) / 2.302585);
        I = mix(Is, fb * bul + fd * Id * trunc, sv);
        // 옆으로 누운 나선: 원반 가운데 암흑대 (살짝 한쪽으로)
        if (type > 1.5 && type < 5.5 && q < 0.35) {
            float w = 0.03 + 0.1 * q;
            I *= 1.0 - 0.75 * exp(-((y - 0.02) / w) * ((y - 0.02) / w)) * (1.0 - smoothstep(0.7, 1.0, abs(x)));
        }
    }
    // 사각형 끝에서 잘리지 않게
    I *= 1.0 - smoothstep(0.85, 1.0, max(abs(uv.x), abs(uv.y)));

    // 성운·행성상성운은 문턱 → 400배까지 (밝은 성운 속이 평평하게 타지 않도록)
    float span = (code > 12.5 && tile > -0.5) ? 2.6 : 2.0;
    float b = clamp(log(max(I, 1e-6) / thr) / (span * 2.302585), 0.0, 1.0);   // 문턱 → 100배 밝은 곳까지 단계
    b = b * b * (3.0 - 2.0 * b);
    float k = b * prm.b * min(1.0, ColorModulator.a * 1.5);
    // 사진 판 성운·성단은 빛무리를 옅게 (그 위 실제 별이 묻히지 않게) — 구상성단 0.6 · 성운 0.7
    if (tile > -0.5 && code > 11.5) k *= code < 12.5 ? 0.6 : 0.7;
    if (k <= 0.002) discard;
    // 어두운 빛은 눈에 회색. 성운·행성상성운은 아주 밝은 곳만 옅은 청록(O III), 구상성단은 옅은 노랑
    vec3 c = vec3(0.93, 0.93, 0.95);
    float hi = smoothstep(0.7, 1.0, b);
    if (code > 12.5) c = mix(c, code > 13.5 ? vec3(0.62, 1.0, 0.88) : vec3(0.72, 1.0, 0.85), hi * (code > 13.5 ? 0.4 : 0.25));
    else if (code > 11.5) c = mix(c, vec3(1.0, 0.95, 0.82), hi * 0.4);
    fragColor = vec4(c, k);
}

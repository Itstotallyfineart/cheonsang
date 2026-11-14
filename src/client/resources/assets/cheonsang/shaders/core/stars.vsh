#version 330
#extension GL_ARB_separate_shader_objects : require

// 천상 별: 화면에서 늘 같은 크기의 점 (망원경으로 확대해도 점으로 남고, 쌍성은 간격만 벌어짐)
// 정점 4개가 모두 별 중심 위치. UV0.x = 등급, UV0.y = 모서리 번호(0~3) / Color.rgb = 별색
// (26.3 은 셰이더를 Vulkan 방식으로 컴파일 → gl_VertexID 를 못 씀)
// ColorModulator: r = 하늘 어두움(박명·비), g = 한계등급, b = 크기 배율, a = 별 상 최소 반지름 px

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec2 corner;
layout(location = 1) out vec4 starColor;

void main() {
    int id = int(UV0.y + 0.5);
    vec2 c = vec2((id == 1 || id == 2) ? 1.0 : -1.0, (id >= 2) ? 1.0 : -1.0);
    float mag = UV0.x;
    float limit = ColorModulator.g;
    // 지평선: TextureMat[2].xyz = 월드 위쪽(J2000), .w = 소광 계수 k (끄면 0) / TextureMat[3].x = 굴절 켜짐
    vec3 p = normalize(Position);
    vec3 upJ = TextureMat[2].xyz;
    float s = clamp(dot(p, upJ), -1.0, 1.0);
    float altD = degrees(asin(s));
    vec3 pos = Position;
    if (TextureMat[3].x > 0.5 && altD > -1.5 && length(upJ) > 0.5) {
        float h = max(altD, -0.9);
        float refr = radians(1.02 / tan(radians(h + 10.3 / (h + 5.11))) / 60.0);
        vec3 t = upJ - p * s;
        float tl = length(t);
        if (tl > 1e-5) pos = (p * cos(refr) + (t / tl) * sin(refr)) * length(Position);
    }
    // 대기 소광 Δm = k·(X − 1), X = Kasten–Young 대기량 (천정 대비)
    float hh = max(altD, -0.5);
    float airX = min(40.0, 1.0 / (sin(radians(hh)) + 0.50572 * pow(hh + 6.07995, -1.6364)));
    float kExt = TextureMat[2].w;
    mag += kExt * (airX - 1.0);
    // 한계등급 +0.5 까지 부드럽게 사라짐, 한계보다 4.5등 밝으면 최대
    // 크기(반지름 100 구면, 시야 70° 기준 단위): 한계 근처 0.2 → 밝을수록 커짐
    // 화면 반지름 1.5px + 가우스 (stars.fsh)
    float alpha = pow(clamp((limit + 0.5 - mag) / 5.0, 0.0, 1.0), 1.4);
    float want = clamp(0.22 * pow(10.0, -0.1 * (mag - limit)), 0.02, 1.3) * ColorModulator.b;
    // 화면 픽셀 단위로 반지름을 정함 (TextureMat[1].x = 화면 높이 px). 크기 단위 1 = 시야 70° 기준 반지름 100 구면의 1
    // 반지름 = 크기 × 0.5 → 화면 높이 비율 0.5 × 1.428 / 100. 최소 반지름 1.5px, 작아지는 만큼 진하기로 보상 (밝기 총량 유지)
    float screenH = max(TextureMat[1].x, 1.0);
    float wantPx = want * 0.5 * 0.01428 * screenH * 0.5;
    // ColorModulator.a = 별 상 최소 반지름 px (회절·시상 — 배율이 구경×2 를 넘으면 커져 흐려짐, 망원경 밖 1)
    float r = max(wantPx, max(1.5, ColorModulator.a));
    alpha *= min(1.0, (wantPx / r) * (wantPx / r)) * 1.25;
    vec4 view = ModelViewMat * vec4(pos, 1.0);
    // 사각형 반 폭 = 2r (가우스 꼬리). 시야가 좁아져도(ProjMat[1][1] 증가) 화면 크기 유지
    float ndcHalf = 2.0 * r / (screenH * 0.5);
    view.xy += c * (ndcHalf * -view.z / ProjMat[1][1]);
    gl_Position = ProjMat * view;
    corner = c * 0.5 + 0.5;
    // 달 원판 안의 별은 안 그림 (TextureMat[0] = 달 방향, cos(달 반지름))
    if (dot(normalize(Position), TextureMat[0].xyz) > TextureMat[0].w) {
        alpha = 0.0;
    }
    // 색 진하기 = 한계등급보다 얼마나 밝은지 — 한계 근처는 거의 흰색, 밝은 별만 옅은 색(최대 55%). 망원경은 한계등급이 높아져 같은 별이 더 밝아 보여 자연히 색이 더 보임("색 빠짐" 효과, TextureMat[3].y = 켜짐).
    // 끄면 모든 별 같은 진하기 0.55
    float sat = TextureMat[3].y > 0.5 ? 0.55 * clamp((limit - mag - 1.5) / 4.0, 0.12, 1.0) : 0.55;
    vec3 col = mix(vec3(1.0), Color.rgb, sat);
    // 지평선 붉어짐: R 0.09 · V 0.172 · B 0.30 등/대기량 (가장 밝은 채널 1)
    // 카노푸스 보정: 대기량 10(고도 약 5.5°)에서 멈추고 지각상 절반 세기, 희미한 별은 색 진하기만큼만 (지평선에서도 주황 정도)
    if (kExt > 0.0) {
        float xr = min(airX, 10.0) - 1.0;
        vec3 ext = vec3(pow(10.0, 0.2 * 0.082 * xr), 1.0, pow(10.0, -0.2 * 0.128 * xr));
        ext /= max(ext.r, max(ext.g, ext.b));
        float rs = clamp(sat / 0.55, 0.15, 1.0);
        col *= mix(vec3(1.0), ext, rs);
    }
    // 반짝임: TextureMat[3].z = 세기, .w = 시각(초) — 지평선 쪽 강하게(대기량), 별마다 다른 위상
    // 대기량 3(고도 약 19°)에서 멈추고 세기 0.43배·속도 절반 → 지평선 최대 ±15%
    float tw = TextureMat[3].z;
    if (tw > 0.0) {
        float ph = dot(Position, vec3(12.9898, 78.233, 37.719));
        float n = sin(TextureMat[3].w * 3.6 + ph) * 0.6 + sin(TextureMat[3].w * 6.5 + ph * 1.7) * 0.4;
        alpha *= max(0.0, 1.0 + tw * 0.43 * min(airX, 3.0) / 3.0 * n);
    }
    starColor = vec4(col, alpha * ColorModulator.r);
}

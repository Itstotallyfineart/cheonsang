#version 330
#extension GL_ARB_separate_shader_objects : require

// 천상 1.2.0: 한 채널(R8) 흑백 그림 — r 을 rgb 로 펼쳐서 position_tex 와 같은 결과 (현실 은하수)

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    float v = texture(Sampler0, texCoord0).r;
    fragColor = vec4(v, v, v, 1.0) * ColorModulator;
}

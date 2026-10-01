#version 150

uniform sampler2D DiffuseSampler;

uniform float Strength;
uniform float Time;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec3 col = texture(DiffuseSampler, uv).rgb;

    // 呼吸式脉动：暗红强度随时间轻微起伏，营造审判压迫感
    float pulse = 0.85 + 0.15 * sin(Time * 2.2);

    // 暗红滤镜：整体压暗亮度，色相推向深红
    float gray = dot(col, vec3(0.299, 0.587, 0.114));
    vec3 tinted = vec3(
        gray * 0.55 + col.r * 0.18,
        gray * 0.08,
        gray * 0.08
    );

    // 暗角：屏幕边缘进一步压暗成深红
    float dist = distance(uv, vec2(0.5));
    float vignette = smoothstep(0.35, 0.95, dist);
    tinted *= mix(1.0, 0.7, vignette);

    // 按强度混合原图与暗红滤镜
    float s = clamp(Strength, 0.0, 1.0) * pulse;
    vec3 finalColor = mix(col, tinted, s);

    fragColor = vec4(finalColor, 1.0);
}
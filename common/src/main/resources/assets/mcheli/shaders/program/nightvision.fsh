#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

//制作者 TV90
void main() {
    vec4 c = texture(DiffuseSampler, texCoord);
    float yellowness = (c.r + c.g) * 0.5 - c.b;
    const float threshold = 0.2;
    float isYellow = step(threshold, yellowness);
    vec3 enhanced = clamp(c.rgb + vec3(0.1, 0.1, -0.05), 0.0, 1.0);
    vec3 result = mix(c.rgb, enhanced, isYellow);
    fragColor = vec4(result, 1.0);
}

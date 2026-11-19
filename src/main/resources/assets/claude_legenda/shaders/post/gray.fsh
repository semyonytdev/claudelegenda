#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 c = texture(InSampler, texCoord);

    // Полное обесцвечивание мира.
    float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));

    // Насыщенно-красные пиксели (красные звёзды грусти) оставляем цветными,
    // иначе шейдер закрасил бы их в серый вместе со всем миром.
    float redDominance = c.r - max(c.g, c.b);
    float keep = smoothstep(0.30, 0.50, redDominance) * smoothstep(0.45, 0.70, c.r);

    fragColor = vec4(mix(vec3(g), c.rgb, keep), 1.0);
}

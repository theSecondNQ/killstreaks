#version 150
uniform sampler2D DiffuseSampler;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 scene=texture(DiffuseSampler,texCoord).rgb;
    float luminance=dot(scene,vec3(0.299,0.587,0.114));
    float cold=clamp(luminance*0.58+0.03,0.03,0.61);
    // Fullbright white heat remains distinct from the dim gray terrain.
    // 中文：全亮白色热目标与较暗的灰色地形保持明显区分。
    float heat=smoothstep(0.985,0.998,min(scene.r,min(scene.g,scene.b)));
    fragColor=vec4(vec3(mix(cold,1.0,heat)),1.0);
}

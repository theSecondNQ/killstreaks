#version 150
uniform sampler2D DiffuseSampler;
uniform sampler2D HeatSampler;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 scene=texture(DiffuseSampler,texCoord).rgb;
    float luminance=dot(scene,vec3(0.299,0.587,0.114));
    float cold=clamp(luminance*0.52+0.035,0.035,0.555);
    // Only depth-tested living model vertices supply heat. Bright sky and snow stay cold.
    // 中文：只有通过深度检测的生物模型顶点产生热信号，明亮天空和积雪仍显示为冷色。
    float heat=texture(HeatSampler,texCoord).r;
    fragColor=vec4(vec3(mix(cold,0.98,heat)),1.0);
}

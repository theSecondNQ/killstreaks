#version 150
uniform sampler2D DiffuseSampler;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    float alpha=texture(DiffuseSampler,texCoord).a*vertexColor.a;
    if(alpha<0.002)discard;
    // Texture self-emission only: no lighting, fog, scene sampling or dynamic-light injection.
    // 中文：仅显示纹理自发光，不使用光照、雾、场景采样或动态光源注入。
    fragColor=vec4(vertexColor.rgb,alpha);
}

#version 150
uniform sampler2D DiffuseSampler;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec4 source=texture(DiffuseSampler,texCoord);
    float alpha=source.a*vertexColor.a;
    if(alpha<0.002)discard;
    fragColor=vec4(source.rgb*vertexColor.rgb,alpha);
}

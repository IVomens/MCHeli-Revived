#version 330 core

out vec4 FragColor;

in vec3 TexCoord;
in vec3 VertexColor;

uniform sampler2D tex;
//uniform vec3 lightPos;
//uniform vec3 viewPos;
uniform vec4 color;

void main(){
    vec2 uv = TexCoord.xy;
    FragColor = texture(tex, uv) * vec4(VertexColor, 1.0) * color;
}


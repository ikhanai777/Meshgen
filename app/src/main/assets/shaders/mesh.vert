#version 300 es
uniform mat4 uMvp;
uniform mat4 uView;
in vec3 aPos;
out vec3 vView;
void main() {
    vView = (uView * vec4(aPos, 1.0)).xyz;
    gl_Position = uMvp * vec4(aPos, 1.0);
}

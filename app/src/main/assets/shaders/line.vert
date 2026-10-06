#version 300 es
uniform mat4 uMvp;
in vec3 aPos;
void main() {
    gl_Position = uMvp * vec4(aPos, 1.0);
}

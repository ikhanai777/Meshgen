#version 300 es
precision highp float;
in vec3 vView;
uniform vec3 uKeyDir;
uniform vec3 uKeyColor;
uniform vec3 uFillDir;
uniform vec3 uFillColor;
uniform vec3 uAmbient;
uniform vec3 uBase;
uniform vec3 uBack;
uniform float uSpec;
uniform float uRim;
out vec4 fragColor;
void main() {
    // Flat shading from screen-space derivatives: correct facets without per-face vertex copies.
    vec3 n = normalize(cross(dFdx(vView), dFdy(vView)));
    vec3 v = normalize(-vView);
    if (dot(n, v) < 0.0) n = -n;
    // Back faces (seen through holes or on inside-out parts) are tinted so problems are visible.
    vec3 base = gl_FrontFacing ? uBase : uBack;
    float key = max(dot(n, uKeyDir), 0.0);
    float fill = max(dot(n, uFillDir), 0.0);
    vec3 h = normalize(uKeyDir + v);
    float spec = pow(max(dot(n, h), 0.0), 48.0) * uSpec;
    float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0) * uRim;
    vec3 c = base * (uAmbient + uKeyColor * key + uFillColor * fill) + uKeyColor * spec + vec3(rim);
    fragColor = vec4(pow(clamp(c, 0.0, 1.0), vec3(1.0 / 2.2)), 1.0);
}

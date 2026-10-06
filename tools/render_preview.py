# Desktop preview of the in-app viewer: renders sample exports with the app's own GLSL shaders.
# Usage: python tools/render_preview.py app/src/main/assets/shaders core/build/sample-exports out.png
# Needs: pip install moderngl trimesh pillow numpy, plus a headless EGL driver (e.g. Mesa).
# Offscreen reproduction of MeshRenderer.kt using the app's own shader files, for a visual check.
import sys, math, numpy as np, moderngl, trimesh
from PIL import Image, ImageDraw
shaders, exports, out = sys.argv[1], sys.argv[2], sys.argv[3]
ctx = moderngl.create_standalone_context(backend='egl')
rd = lambda n: open(f"{shaders}/{n}").read()
mesh_prog = ctx.program(vertex_shader=rd('mesh.vert'), fragment_shader=rd('mesh.frag'))
line_prog = ctx.program(vertex_shader=rd('line.vert'), fragment_shader=rd('line.frag'))
W, H = 540, 540
FOV = 35.0

def look_at(eye, target, up):
    f = target - eye; f /= np.linalg.norm(f)
    s = np.cross(f, up); s /= np.linalg.norm(s)
    u = np.cross(s, f)
    m = np.identity(4, dtype='f4')
    m[0, :3] = s; m[1, :3] = u; m[2, :3] = -f
    m[:3, 3] = -m[:3, :3] @ eye
    return m

def perspective(fovy, aspect, near, far):
    f = 1 / math.tan(math.radians(fovy) / 2)
    m = np.zeros((4, 4), dtype='f4')
    m[0, 0] = f / aspect; m[1, 1] = f; m[2, 2] = (far + near) / (near - far); m[2, 3] = 2 * far * near / (near - far); m[3, 2] = -1
    return m

PRESETS = {
 'STUDIO': dict(uKeyDir=(0.5,0.7,0.6), uKeyColor=(0.85,0.85,0.88), uFillDir=(-0.7,-0.1,0.5), uFillColor=(0.22,0.26,0.30), uAmbient=(0.10,0.11,0.12), uBase=(0.62,0.64,0.68), uSpec=0.25, uRim=0.10),
 'CLAY': dict(uKeyDir=(0.2,0.5,1), uKeyColor=(0.70,0.68,0.65), uFillDir=(-0.3,-0.4,0.8), uFillColor=(0.30,0.30,0.32), uAmbient=(0.22,0.22,0.22), uBase=(0.72,0.66,0.60), uSpec=0.03, uRim=0.0),
 'CONTRAST': dict(uKeyDir=(0.8,0.6,0.3), uKeyColor=(1,1,1), uFillDir=(-0.8,0.2,0.2), uFillColor=(0.05,0.07,0.09), uAmbient=(0.03,0.03,0.04), uBase=(0.55,0.57,0.60), uSpec=0.6, uRim=0.30),
}

def render(name, preset, wire, yaw=-60.0, pitch=25.0):
    m = trimesh.load(f"{exports}/{name}.obj", force='mesh', process=False)
    pos = np.asarray(m.vertices, dtype='f4'); idx = np.asarray(m.faces, dtype='u4')
    lo, hi = pos.min(0), pos.max(0); c = (lo + hi) / 2; radius = max(np.linalg.norm(hi - lo) / 2, 1)
    dist = radius / math.sin(math.radians(FOV / 2)) * 1.15
    y, p = math.radians(yaw), math.radians(pitch)
    eye = c + dist * np.array([math.cos(p)*math.cos(y), math.cos(p)*math.sin(y), math.sin(p)])
    view = look_at(eye.astype('f4'), c.astype('f4'), np.array([0, 0, 1], 'f4'))
    proj = perspective(FOV, W / H, max(dist - radius * 3, dist * 0.01), dist + radius * 6)
    mvp = proj @ view
    fbo = ctx.simple_framebuffer((W, H), samples=4); fbo.use()
    ctx.clear(0.043, 0.047, 0.055, 1.0)
    ctx.enable(moderngl.DEPTH_TEST); ctx.depth_func = '<='
    # grid
    size = max(hi[0]-lo[0], hi[1]-lo[1], 10); step = 50 if size > 400 else 20 if size > 120 else 5 if size < 25 else 10
    half = math.ceil(size * 0.9 / step) * step; gx = int(c[0]/step)*step; gy = int(c[1]/step)*step; z = lo[2]
    g = []
    for k in range(int(2*half/step)+1):
        o = -half + k*step
        g += [gx+o, gy-half, z, gx+o, gy+half, z, gx-half, gy+o, z, gx+half, gy+o, z]
    line_prog['uMvp'].write(mvp.T.astype('f4').tobytes())
    ctx.enable(moderngl.BLEND); ctx.depth_mask = False
    line_prog['uColor'].value = (0.36, 0.40, 0.46, 0.35)
    ctx.vertex_array(line_prog, [(ctx.buffer(np.array(g,'f4').tobytes()), '3f', 'aPos')]).render(moderngl.LINES)
    ctx.depth_mask = True; ctx.disable(moderngl.BLEND)
    # mesh
    vbo = ctx.buffer(pos.tobytes())
    mesh_prog['uMvp'].write(mvp.T.astype('f4').tobytes()); mesh_prog['uView'].write(view.T.astype('f4').tobytes())
    for k, v in PRESETS[preset].items():
        if k.endswith('Dir'): v = tuple(np.array(v)/np.linalg.norm(v))
        mesh_prog[k].value = v
    mesh_prog['uBack'].value = (0.85, 0.45, 0.20)
    ctx.polygon_offset = (1.5, 2.0)
    closed = trimesh.load(f"{exports}/{name}.stl", force='mesh').is_watertight
    if closed: ctx.enable(moderngl.CULL_FACE)
    ctx.vertex_array(mesh_prog, [(vbo, '3f', 'aPos')], ctx.buffer(idx.tobytes())).render(moderngl.TRIANGLES)
    ctx.polygon_offset = (0, 0); ctx.disable(moderngl.CULL_FACE)
    if wire:
        edges = np.unique(np.sort(np.concatenate([idx[:, [0,1]], idx[:, [1,2]], idx[:, [2,0]]]), axis=1), axis=0).astype('u4')
        ctx.enable(moderngl.BLEND)
        line_prog['uColor'].value = (0.36, 0.88, 0.90, 0.55)
        ctx.vertex_array(line_prog, [(vbo, '3f', 'aPos')], ctx.buffer(edges.tobytes())).render(moderngl.LINES)
        ctx.disable(moderngl.BLEND)
    plain = ctx.simple_framebuffer((W, H)); ctx.copy_framebuffer(plain, fbo)
    img = Image.frombytes('RGB', (W, H), plain.read(components=3)).transpose(Image.FLIP_TOP_BOTTOM)
    fbo.release(); plain.release()
    d = ImageDraw.Draw(img); d.text((12, 12), f"{name} · {preset}{' · wireframe' if wire else ''}", fill=(154, 161, 172))
    return img

shots = [('calibration_cube','STUDIO',False,-60,25), ('ring','STUDIO',True,-60,35), ('dense_sphere','CLAY',False,-60,25),
         ('cylinder','CONTRAST',False,-60,25), ('damaged_box','STUDIO',False,-30,40), ('sphere','STUDIO',True,-60,25)]
imgs = [render(*s) for s in shots]
sheet = Image.new('RGB', (W*3, H*2))
for i, im in enumerate(imgs): sheet.paste(im, ((i % 3)*W, (i//3)*H))
sheet.save(out); print('saved', out)

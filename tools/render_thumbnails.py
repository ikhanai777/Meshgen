# Renders template thumbnails for the in-app gallery using the app's own viewer shaders.
# Usage: python tools/render_thumbnails.py app/src/main/assets/shaders core/build/template-exports app/src/main/assets/thumbnails [sheet.png]
# Run `./gradlew :core:test --tests '*TemplatesTest*'` first to produce the OBJ exports.
# Needs: pip install moderngl trimesh pillow numpy, plus a headless EGL driver (e.g. Mesa).
import sys, os, glob, math, numpy as np, moderngl, trimesh
from PIL import Image, ImageDraw

shaders, exports, outdir = sys.argv[1], sys.argv[2], sys.argv[3]
sheet_path = sys.argv[4] if len(sys.argv) > 4 else None
os.makedirs(outdir, exist_ok=True)
ctx = moderngl.create_standalone_context(backend='egl')
rd = lambda n: open(f"{shaders}/{n}").read()
prog = ctx.program(vertex_shader=rd('mesh.vert'), fragment_shader=rd('mesh.frag'))
S = 320
FOV = 30.0
BG = (0x12/255, 0x14/255, 0x18/255)

def look_at(eye, target, up):
    f = target - eye; f /= np.linalg.norm(f)
    s = np.cross(f, up); s /= np.linalg.norm(s); u = np.cross(s, f)
    m = np.identity(4, dtype='f4'); m[0, :3] = s; m[1, :3] = u; m[2, :3] = -f; m[:3, 3] = -m[:3, :3] @ eye
    return m

def perspective(fovy, aspect, near, far):
    f = 1 / math.tan(math.radians(fovy) / 2); m = np.zeros((4, 4), dtype='f4')
    m[0, 0] = f / aspect; m[1, 1] = f; m[2, 2] = (far + near) / (near - far); m[2, 3] = 2 * far * near / (near - far); m[3, 2] = -1
    return m

images = []
for path in sorted(glob.glob(f"{exports}/*.obj")):
    name = os.path.splitext(os.path.basename(path))[0]
    m = trimesh.load(path, force='mesh', process=False)
    pos = np.asarray(m.vertices, dtype='f4'); idx = np.asarray(m.faces, dtype='u4')
    lo, hi = pos.min(0), pos.max(0); c = (lo + hi) / 2; radius = np.linalg.norm(hi - lo) / 2
    dist = radius / math.sin(math.radians(FOV / 2)) * 1.02
    y, p = math.radians(-55), math.radians(30)
    eye = c + dist * np.array([math.cos(p)*math.cos(y), math.cos(p)*math.sin(y), math.sin(p)])
    view = look_at(eye.astype('f4'), c.astype('f4'), np.array([0, 0, 1], 'f4'))
    mvp = perspective(FOV, 1.0, dist * 0.1, dist * 3) @ view
    fbo = ctx.simple_framebuffer((S, S), samples=4); fbo.use()
    ctx.clear(*BG, 1.0); ctx.enable(moderngl.DEPTH_TEST | moderngl.CULL_FACE)
    prog['uMvp'].write(mvp.T.astype('f4').tobytes()); prog['uView'].write(view.T.astype('f4').tobytes())
    for k, v in dict(uKeyDir=(0.5,0.7,0.6), uKeyColor=(0.85,0.85,0.88), uFillDir=(-0.7,-0.1,0.5), uFillColor=(0.22,0.26,0.30),
                     uAmbient=(0.10,0.11,0.12), uBase=(0.62,0.64,0.68), uBack=(0.85,0.45,0.2)).items():
        prog[k].value = tuple(np.array(v)/np.linalg.norm(v)) if k.endswith('Dir') else v
    prog['uSpec'].value = 0.25; prog['uRim'].value = 0.10
    ctx.vertex_array(prog, [(ctx.buffer(pos.tobytes()), '3f', 'aPos')], ctx.buffer(idx.tobytes())).render(moderngl.TRIANGLES)
    plain = ctx.simple_framebuffer((S, S)); ctx.copy_framebuffer(plain, fbo)
    img = Image.frombytes('RGB', (S, S), plain.read(components=3)).transpose(Image.FLIP_TOP_BOTTOM)
    fbo.release(); plain.release()
    img.save(f"{outdir}/{name}.webp", quality=82, method=6)
    images.append((name, img))
    print(name, os.path.getsize(f"{outdir}/{name}.webp"), 'bytes')

if sheet_path:
    cols = 6; rows = math.ceil(len(images) / cols); t = 200
    sheet = Image.new('RGB', (cols * t, rows * (t + 18)), (11, 12, 14)); d = ImageDraw.Draw(sheet)
    for i, (n, im) in enumerate(images):
        x, yy = (i % cols) * t, (i // cols) * (t + 18)
        sheet.paste(im.resize((t, t)), (x, yy)); d.text((x + 6, yy + t + 3), n, fill=(154, 161, 172))
    sheet.save(sheet_path)

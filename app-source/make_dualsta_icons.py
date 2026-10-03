from pathlib import Path
from PIL import Image, ImageDraw
src = Path(r"C:\Users\AKSWAP\.codex\generated_images\01a0f782-fc46-75a3-a659-83a851d13231\exec-7866cb6c-d1dc-4af6-bdf1-2e4380c712fd.png")
res = Path(r"C:\Users\AKSWAP\Documents\Codex\2026-09-27\https-chatgpt-com-c-6ab754c0-2c90\aistudio_export_0055\app\src\main\res")
im = Image.open(src).convert("RGBA")
bbox = im.getchannel("A").getbbox()
if not bbox: raise RuntimeError("Generated icon has no visible pixels")
mark = im.crop(bbox)
def fit_mark(canvas_size, fraction):
    scale=min((canvas_size*fraction)/mark.width,(canvas_size*fraction)/mark.height)
    return mark.resize((round(mark.width*scale),round(mark.height*scale)),Image.Resampling.LANCZOS)
def centered(canvas,icon): canvas.alpha_composite(icon,((canvas.width-icon.width)//2,(canvas.height-icon.height)//2))
fg=Image.new("RGBA",(432,432),(0,0,0,0)); centered(fg,fit_mark(432,0.61)); fg.save(res/"drawable-nodpi"/"ic_launcher_foreground_asset.png",optimize=True)
mono=Image.new("RGBA",fg.size,(255,255,255,0)); mono.putalpha(fg.getchannel("A")); mono.save(res/"drawable-nodpi"/"ic_launcher_monochrome.png",optimize=True)
for density,size in {"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}.items():
    icon=fit_mark(size,0.73)
    for rounded in (False,True):
        canvas=Image.new("RGBA",(size,size),(0,0,0,0)); draw=ImageDraw.Draw(canvas); bg=(8,16,30,255)
        if rounded: draw.ellipse((0,0,size-1,size-1),fill=bg)
        else: draw.rounded_rectangle((0,0,size-1,size-1),radius=max(2,round(size*0.22)),fill=bg)
        centered(canvas,icon)
        canvas.save(res/f"mipmap-{density}"/("ic_launcher_round.webp" if rounded else "ic_launcher.webp"),"WEBP",lossless=True,method=6)

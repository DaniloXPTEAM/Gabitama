#!/usr/bin/env python3
"""Gera sequências WebP com alfa em loop a partir das poses aprovadas do Quarto.

Movimento procedural (respiração + pulso), sem arte nova: cada quadro é a própria pose
PNG transparente, ancorada nos pés, com escala vertical senoidal. Loop contínuo: o período
completo cabe exatamente no número de quadros.

Uso (a partir da raiz do repositório):
    python3 scripts/hb/gen_quarto_breathing_webp.py <pasta-assets-main>

Requer Pillow com suporte a WebP. Saída: assets/anim/<pasta>/fNNN.webp (360x480).
Implementação própria do GabiTama (MIT).
"""
import math
import os
import sys

from PIL import Image

SIZE = (360, 480)
QUALITY = 82

# (pasta de saída, pose de origem, quadros do loop, amplitude vertical, amplitude lateral)
JOBS = [
    ("scene_comer", "gabi-comer.png", 48, 0.010, 0.003),
    ("scene_ler", "gabi-ler.png", 60, 0.008, 0.002),
    ("scene_dormir", "gabi-dormir.png", 72, 0.014, 0.003),
    # Reação ao alimentar: pulso mais marcado e curto.
    ("scene_comendo_reacao", "gabi-comer.png", 24, 0.030, 0.006),
]


def frame(pose: Image.Image, t: float, amp_y: float, amp_x: float) -> Image.Image:
    """t em [0, 1). Escala ancorada na base (pés), loop senoidal."""
    sy = 1.0 + amp_y * math.sin(2 * math.pi * t)
    sx = 1.0 - amp_x * math.sin(2 * math.pi * t)
    w, h = pose.size
    scaled = pose.resize((max(1, round(w * sx)), max(1, round(h * sy))), Image.LANCZOS)
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    # Ancora na base: o centro horizontal fica fixo e a parte inferior não sobe.
    canvas.paste(scaled, ((w - scaled.width) // 2, h - scaled.height), scaled)
    return canvas.resize(SIZE, Image.LANCZOS)


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    assets = sys.argv[1]
    for folder, pose_name, n, amp_y, amp_x in JOBS:
        src = Image.open(os.path.join(assets, pose_name)).convert("RGBA")
        out_dir = os.path.join(assets, "anim", folder)
        os.makedirs(out_dir, exist_ok=True)
        for i in range(n):
            img = frame(src, i / n, amp_y, amp_x)
            img.save(os.path.join(out_dir, f"f{i + 1:03d}.webp"), "WEBP", quality=QUALITY, method=6)
        print(f"{folder}: {n} quadros de {pose_name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

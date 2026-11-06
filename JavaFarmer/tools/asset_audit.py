#!/usr/bin/env python3
"""Small dependency-free integrity/size audit for shipped Java Farmer assets."""
from pathlib import Path
import struct
import wave
import zlib

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "assets"
MAX_FILE = 5 * 1024 * 1024
errors = []
notes = []

def check_png(path: Path):
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("assinatura PNG inválida")
    offset = 8
    saw_ihdr = False
    saw_iend = False
    while offset < len(data):
        if offset + 12 > len(data):
            raise ValueError("chunk PNG truncado")
        length = struct.unpack(">I", data[offset:offset+4])[0]
        kind = data[offset+4:offset+8]
        end = offset + 12 + length
        if end > len(data):
            raise ValueError("chunk PNG ultrapassa o fim do arquivo")
        payload = data[offset+8:offset+8+length]
        expected_crc = struct.unpack(">I", data[offset+8+length:end])[0]
        actual_crc = zlib.crc32(kind + payload) & 0xffffffff
        if expected_crc != actual_crc:
            raise ValueError(f"CRC inválido no chunk {kind.decode('ascii', 'replace')}")
        if kind == b"IHDR":
            if length != 13:
                raise ValueError("IHDR inválido")
            width, height = struct.unpack(">II", payload[:8])
            if width <= 0 or height <= 0 or width > 8192 or height > 8192:
                raise ValueError("dimensões PNG inválidas")
            saw_ihdr = True
        if kind == b"IEND":
            saw_iend = True
            break
        offset = end
    if not saw_ihdr or not saw_iend:
        raise ValueError("PNG sem IHDR/IEND")

def check_wav(path: Path):
    with wave.open(str(path), "rb") as audio:
        if audio.getnchannels() not in (1, 2):
            raise ValueError("número de canais não suportado")
        if audio.getsampwidth() not in (1, 2, 3, 4):
            raise ValueError("profundidade de áudio inválida")
        if audio.getframerate() < 8000 or audio.getnframes() <= 0:
            raise ValueError("áudio vazio ou taxa de amostragem inválida")

def png_size(path: Path):
    data = path.read_bytes()
    return struct.unpack(">II", data[16:24])

def check_fnt(path: Path):
    """AngelCode BMFont (text): page must exist, atlas size must match and glyphs must fit inside it."""
    import re
    text = path.read_text(encoding="utf-8")
    common = re.search(r"^common .*?scaleW=(\d+) scaleH=(\d+) pages=(\d+)", text, re.M)
    page = re.search(r'^page id=0 file="([^"]+)"', text, re.M)
    if not common or not page:
        raise ValueError("cabeçalho BMFont incompleto")
    if int(common.group(3)) != 1:
        raise ValueError("apenas uma página de atlas é suportada")
    atlas = path.parent / page.group(1)
    if not atlas.exists():
        raise ValueError(f"atlas ausente: {page.group(1)}")
    w, h = int(common.group(1)), int(common.group(2))
    if png_size(atlas) != (w, h):
        raise ValueError("dimensões do atlas diferentes do .fnt")
    chars = re.findall(r"^char id=(\d+) x=(\d+) y=(\d+) width=(\d+) height=(\d+)", text, re.M)
    if len(chars) < 95:
        raise ValueError("fonte com poucos glifos")
    ids = {int(c[0]) for c in chars}
    for required in (ord("A"), ord("a"), ord("0"), 0xE7, 0xE3, 0xC7, 0xB7):
        if required not in ids:
            raise ValueError(f"glifo obrigatório ausente: U+{required:04X}")
    for cid, x, y, cw, ch in chars:
        if int(x) + int(cw) > w or int(y) + int(ch) > h:
            raise ValueError(f"glifo {cid} fora do atlas")
    if not any(p.name.startswith("OFL") for p in path.parent.iterdir()):
        raise ValueError("licença OFL da fonte ausente")

files = [p for p in ASSETS.rglob("*") if p.is_file()]
if not files:
    errors.append("nenhum asset encontrado")
for path in files:
    size = path.stat().st_size
    if size == 0:
        errors.append(f"arquivo vazio: {path.relative_to(ROOT)}")
    if size > MAX_FILE:
        notes.append(f"arquivo acima de 5 MiB: {path.relative_to(ROOT)} ({size/1024/1024:.2f} MiB)")
    try:
        if path.suffix.lower() == ".png":
            check_png(path)
        elif path.suffix.lower() == ".wav":
            check_wav(path)
        elif path.suffix.lower() == ".fnt":
            check_fnt(path)
    except Exception as exc:
        errors.append(f"{path.relative_to(ROOT)}: {exc}")

print(f"Assets auditados: {len(files)}")
for note in notes:
    print("AVISO:", note)
for error in errors:
    print("ERRO:", error)
if errors:
    raise SystemExit(1)
print("ASSET AUDIT: PASS")

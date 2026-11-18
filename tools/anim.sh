#!/usr/bin/env bash
# Grava uma animação real do viewer (sequência offscreen) e converte para MP4 e GIF com ffmpeg.
#
# Uso:  tools/anim.sh <saida-sem-extensao> "<spec do snap>" [quadros] [fps]
# Ex.:  tools/anim.sh output/tmp/walk "mode=lineup,style=e,set=v1-v2-v3,clip=walk,myaw=60" 60 30
# Gera <saida>.mp4 (H.264) e <saida>.gif (paleta otimizada), e apaga os PNG intermediários
# (KEEP=1 mantém). Requer ffmpeg no PATH.
set -euo pipefail
cd "$(dirname "$0")/.."
out="$1"; spec="$2"; n="${3:-60}"; fps="${4:-30}"
mkdir -p output/tmp; tmp="$(mktemp -d output/tmp/vxanim.XXXXXX)"
tools/snap.sh "$spec,frames=$n,fps=$fps,out=$tmp/f.png" >/dev/null
mkdir -p "$(dirname "$out")"
# libx264 quando existir; no Fedora (ffmpeg-free) cai para libopenh264
if ffmpeg -hide_banner -encoders 2>/dev/null | grep libx264 > /dev/null; then enc=(-c:v libx264 -crf 18)
else enc=(-c:v libopenh264 -b:v 6M); fi
ffmpeg -loglevel error -y -framerate "$fps" -i "$tmp/f_%03d.png" "${enc[@]}" -pix_fmt yuv420p "$out.mp4"
ffmpeg -loglevel error -y -framerate "$fps" -i "$tmp/f_%03d.png" \
  -vf "scale=iw/2:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=192[p];[b][p]paletteuse=dither=bayer:bayer_scale=4" "$out.gif"
if [ -n "${KEEP:-}" ]; then mkdir -p "$out-frames" && mv "$tmp"/*.png "$out-frames/"; fi
rm -rf "$tmp"
echo "anim: $out.mp4 $out.gif ($n quadros a $fps fps)"

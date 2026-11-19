#!/usr/bin/env bash
# QA de áudio em TEMPO REAL sem alto-falante: o OpenAL Soft é forçado ao backend "wave" (ALSOFT_DRIVERS=wave), que
# consome a mistura no ritmo de um dispositivo real e a grava num arquivo. Se o backend falhar, o jogo fica mudo — nunca
# cai no dispositivo do sistema. Mede buracos de silêncio com tools/qa/audio_buracos.py.
# Uso: tools/qa/audio_tempo_real.sh ROTEIRO SAIDA [ms_minimo]
set -euo pipefail
cd "$(dirname "$0")/../.."
ROTEIRO=$1; SAIDA=$2; MIN=${3:-20}
mkdir -p "$SAIDA"
CONF=$(mktemp -d)
trap 'rm -rf "$CONF"' EXIT
printf '[general]\ndrivers = wave\n[wave]\nfile = %s\n' "$PWD/$SAIDA/tempo-real.wav" > "$CONF/alsoft.conf"
ALSOFT_CONF="$CONF/alsoft.conf" ALSOFT_DRIVERS=wave JAVA_OPTS="-Dvx.som=dispositivo ${JAVA_OPTS:-}" \
    game/build/install/game/bin/game --roteiro "$ROTEIRO" "$SAIDA" 60 > "$SAIDA/log.txt" 2>&1
PY=.venv/bin/python; [ -x "$PY" ] || PY=python3
"$PY" tools/qa/audio_buracos.py "$SAIDA/tempo-real.wav" "$MIN"

#!/bin/bash
set -e

WX_DIR="/content/drive/MyDrive/AgentNexora/WX Stage-IV"
NORD_DIR="/content/nord_library"
WINE64_BIN=$(command -v wine64 || echo "/usr/lib/wine/wine64")

echo "=== [1/5] Preparando ferramentas rápidas (7z + wine64) ==="
if [ ! -x "$WINE64_BIN" ] || ! command -v 7z >/dev/null 2>&1; then
    apt-get update -qq
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends -qq wine64 p7zip-full unzip wget
fi
WINE64_BIN=$(command -v wine64 || echo "/usr/lib/wine/wine64")
ln -sf "$WINE64_BIN" /usr/local/bin/wine64
ln -sf "$WINE64_BIN" /usr/local/bin/wine

ln -sf "$WX_DIR/setup_autosampler_76.lua" /content/setup_autosampler_76.lua
ln -sf "$WX_DIR/gerar_sf2_sfz_76samples.py" /content/gerar_sf2_sfz_76samples.py
mkdir -p /content/wxstage_samples "$NORD_DIR"
pip install -q soundfile numpy

echo "=== [2/5] Extraindo REAPER x64 com 7z (0.5s) e instalando WX Stage-IV VST3 ==="
export WINEPREFIX=/root/.wine
mkdir -p "/root/.wine/drive_c/Program Files/Common Files/VST3"
mkdir -p "/root/.wine/drive_c/Program Files/REAPER (x64)"
cp -rf "$WX_DIR/VST3/"* "/root/.wine/drive_c/Program Files/Common Files/VST3/"
ln -sfn "$NORD_DIR" "/root/.wine/drive_c/Nord_Library"

if [ ! -f "$WX_DIR/reaper_install_x64.exe" ]; then
    wget -q --show-progress -O "$WX_DIR/reaper_install_x64.exe" "https://www.reaper.fm/files/7.x/reaper733_x64-install.exe"
fi

7z x "$WX_DIR/reaper_install_x64.exe" -o"/root/.wine/drive_c/Program Files/REAPER (x64)" -y >/dev/null
rm -rf "/root/.wine/drive_c/Program Files/REAPER (x64)/\$PLUGINSDIR"

REAPER_INI_DIR="/root/.wine/drive_c/users/root/AppData/Roaming/REAPER"
mkdir -p "$REAPER_INI_DIR"
cat << 'RINI' > "$REAPER_INI_DIR/reaper.ini"
[REAPER]
waveout_mode=4
dummy_srate=48000
dummy_blocksize=512
vstpath64=C:\Program Files\Common Files\VST3
RINI

echo "=== [3/5] Baixando e Descompactando Pianos (.npno) e Leads/Samples (.nsmp4) ==="
cd "$NORD_DIR"
if [ ! -d "$NORD_DIR/unpacked" ]; then
    wget -c -q --show-progress -O Piano_Banks.zip "https://assets.nordkeyboards.com/media/documents/FeQK0jXe/Nord%20Stage%204%20Piano%20Banks%20Rev%20H.zip"
    wget -c -q --show-progress -O Sample_Banks.zip "https://assets.nordkeyboards.com/media/documents/YzIDiX1j/Nord%20Stage%204%20Sample%20Banks%20Rev%20H.zip"
    wget -c -q --show-progress -O Synth_Presets.zip "https://www.nordkeyboards.com/wt/documents/619/Nord%20Stage%204%20Synth%20Presets%20Rev%20H.zip"
    wget -c -q --show-progress -O Program_Banks.zip "https://www.nordkeyboards.com/wt/documents/617/Nord%20Stage%204%20Program%20Banks%20Rev%20H.zip"

    for z in *.zip; do
        unzip -q -o "$z" -d "$NORD_DIR/unpacked" || true
    done

    find "$NORD_DIR/unpacked" -type f \( -iname "*.npnob" -o -iname "*.nsmpb" -o -iname "*.ns4b" -o -iname "*.zip" \) | while read -r archive; do
        dest_sub="${archive}_extracted"
        mkdir -p "$dest_sub"
        unzip -q -o "$archive" -d "$dest_sub" 2>/dev/null || true
    done
    rm -f "$NORD_DIR"/*.zip
fi

echo "=== [4/5] Lendo chaves internas do WX Stage-IV para Automação em Massa ==="
python3 - << 'PYEOF'
import re
with open("/content/drive/MyDrive/AgentNexora/WX Stage-IV/Standalone/WX Stage-IV.exe", "rb") as f:
    data = f.read()

seen = set()
for m in re.findall(b"[ -~]{4,90}", data):
    s = m.decode("ascii", errors="ignore")
    if any(k in s for k in ["Sound folder", "soundFolder", "sound_folder", "WX Stage", ".settings", "openFile", ".npno", ".nsmp", "ns4p"]):
        if len(s) < 85 and s not in seen:
            seen.add(s)
            print("  [Chave]:", s)
PYEOF

echo "=== [5/5] Abrindo REAPER x64 já com WX Stage-IV e as 38 Regiões! ==="
nohup "$WINE64_BIN" "C:\Program Files\REAPER (x64)\reaper.exe" "Z:\content\setup_autosampler_76.lua" >/dev/null 2>&1 &
echo "␅ TUDO RESTAURADO EM SEGUNDOS E REAPER ABERTO!"

import os, glob, struct, math, shutil, re
import numpy as np
import soundfile as sf

SAMPLES_BASE = "/content/wxstage_samples"
OUT_BASE     = "/content/drive/MyDrive/AgentNexora/soundfonts_wxstage"
os.makedirs(OUT_BASE, exist_ok=True)

NOTES = [
    (21, "A0"),  (24, "C1"),  (27, "Eb1"), (30, "Gb1"), (32, "Ab1"), (34, "Bb1"),
    (36, "C2"),  (38, "D2"),  (40, "E2"),  (42, "Gb2"), (44, "Ab2"), (46, "Bb2"),
    (48, "C3"),  (50, "D3"),  (52, "E3"),  (54, "Gb3"), (56, "Ab3"), (58, "Bb3"),
    (60, "C4"),  (62, "D4"),  (64, "E4"),  (66, "Gb4"), (68, "Ab4"), (70, "Bb4"),
    (72, "C5"),  (74, "D5"),  (76, "E5"),  (78, "Gb5"), (80, "Ab4"), (82, "Bb5"),
    (84, "C6"),  (86, "D6"),  (88, "E6"),  (90, "Gb6"), (93, "A6"),  (96, "C7"),
    (100, "E7"), (104, "Ab7")
]

SUSTAIN_KEYWORDS = [
    "lead", "synth", "str", "pad", "brass", "choir", "organ", "flute",
    "orch", "mellotron", "saw", "mini", "arp", "crumar", "elka", "wind", "horn", "ens"
]

def is_sustained_instrument(name):
    low = name.lower()
    return any(k in low for k in SUSTAIN_KEYWORDS)

def organize_loose_numbered_wavs():
    loose = sorted(glob.glob(os.path.join(SAMPLES_BASE, "*.wav")))
    groups = {}
    for w in loose:
        base = os.path.basename(w)
        m = re.match(r"^(.+?)-(\d{2,3})\.wav$", base)
        if m:
            prefix, idx_str = m.group(1), m.group(2)
            idx = int(idx_str)
            if 1 <= idx <= len(NOTES):
                groups.setdefault(prefix, []).append((idx, w))
    for prefix, items in groups.items():
        if len(items) == len(NOTES):
            target_dir = os.path.join(SAMPLES_BASE, prefix)
            os.makedirs(target_dir, exist_ok=True)
            for idx, src_path in items:
                pitch, nname = NOTES[idx - 1]
                dst_path = os.path.join(target_dir, f"{pitch:03d}_{nname}.wav")
                shutil.move(src_path, dst_path)

def timecents(sec):
    if sec <= 0.001:
        return -12000
    return int(round(1200.0 * math.log2(sec)))

def build_sf2_and_sfz(inst_dir):
    inst_name = os.path.basename(inst_dir.rstrip("/"))
    sf2_path = os.path.join(OUT_BASE, f"{inst_name}.sf2")
    if os.path.exists(sf2_path) and os.path.getsize(sf2_path) < 95 * 1024 * 1024:
        return

    wavs = sorted(glob.glob(os.path.join(inst_dir, "*.wav")))
    if len(wavs) < 10:
        return

    by_pitch = {}
    for w in wavs:
        b = os.path.basename(w)
        try:
            rk = int(b.split("_")[0])
            if rk not in by_pitch:
                by_pitch[rk] = w
        except:
            pass

    raw = []
    for rk in sorted(by_pitch.keys()):
        w = by_pitch[rk]
        data, sr = sf.read(w, dtype="float32", always_2d=True)
        if data.shape[1] == 1:
            data = np.repeat(data, 2, axis=1)
        if np.max(np.abs(data)) < 0.0005:
            continue
        raw.append((rk, sr, data, os.path.basename(w)))

    if not raw:
        print(f" ␏ {inst_name}: amostras em silêncio, pulando.")
        return

    is_sust = is_sustained_instrument(inst_name)
    # Tempo de finalização (Release): 1.50s para Leads/Cordas/Pads, 0.50s para Pianos/EPs
    rel_sec  = 1.50 if is_sust else 0.50
    fade_sec = 0.80 if is_sust else 0.25
    tipo_str = f"SEM LOOP | Release Longo ({rel_sec:.2f}s - Lead/Cordas)" if is_sust else f"SEM LOOP | Release Piano ({rel_sec:.2f}s)"

    zones = []
    for i, (rk, sr, data, fname) in enumerate(raw):
        lo = 0 if i == 0 else (raw[i - 1][0] + rk) // 2 + 1
        hi = 127 if i == len(raw) - 1 else (rk + raw[i + 1][0]) // 2
        zones.append((rk, lo, hi, sr, data, fname))

    # Gerar .SFZ
    sfz_dir = os.path.join(OUT_BASE, f"{inst_name}_sfz")
    sfz_samples_dir = os.path.join(sfz_dir, "samples")
    os.makedirs(sfz_samples_dir, exist_ok=True)
    sfz_lines = [
        f"// {inst_name} - 48kHz Stereo ({len(zones)*2} samples) - {tipo_str}",
        "<group>",
        "ampeg_attack=0.001",
        "ampeg_decay=80.0",
        "ampeg_sustain=100",
        f"ampeg_release={rel_sec:.2f}",
        "loop_mode=no_loop",
        ""
    ]

    smpl_parts = []
    shdr_entries = []
    cur_offset = 0

    for z_idx, (rk, lo, hi, sr, data, fname) in enumerate(zones):
        peak = np.max(np.abs(data))
        if peak > 0.001:
            data = data * (0.92 / peak)

        # Trim de silêncio inicial acima de -65dB
        mono_env = np.max(np.abs(data), axis=1)
        nz = np.where(mono_env > 0.0006)[0]
        start_trim = max(0, int(nz[0]) - 24) if len(nz) > 0 else 0
        data = data[start_trim:]
        n_frames = len(data)

        # Fade-out suave no final do arquivo WAV para nunca cortar seco mesmo se segurar até o fim
        fade_len = min(int(sr * fade_sec), n_frames // 4)
        if fade_len > 64:
            curve = np.linspace(0.0, np.pi, fade_len, dtype=np.float32)
            fade_curve = 0.5 * (1.0 + np.cos(curve))
            data[-fade_len:, 0] *= fade_curve
            data[-fade_len:, 1] *= fade_curve

        left  = np.clip(data[:, 0] * 32767.0, -32768, 32767).astype("<i2")
        right = np.clip(data[:, 1] * 32767.0, -32768, 32767).astype("<i2")

        l_start, l_end = 8, max(32, n_frames - 8)

        out_wav = os.path.join(sfz_samples_dir, fname)
        sf.write(out_wav, np.column_stack([left, right]), sr, subtype="PCM_16")
        sfz_lines.append(
            f"<region> sample=samples/{fname} pitch_keycenter={rk} lokey={lo} hikey={hi} loop_mode=no_loop"
        )

        pad = np.zeros(46, dtype="<i2")
        l_idx = z_idx * 2
        r_idx = z_idx * 2 + 1

        l_s = cur_offset
        l_e = cur_offset + n_frames
        smpl_parts.append(left.tobytes() + pad.tobytes())
        cur_offset += n_frames + 46

        r_s = cur_offset
        r_e = cur_offset + n_frames
        smpl_parts.append(right.tobytes() + pad.tobytes())
        cur_offset += n_frames + 46

        base_n = f"N{rk:03d}"[:16]
        shdr_entries.append((f"{base_n}_L", l_s, l_e, l_s + l_start, l_s + l_end, sr, rk, 0, r_idx, 4))
        shdr_entries.append((f"{base_n}_R", r_s, r_e, r_s + l_start, r_s + l_end, sr, rk, 0, l_idx, 2))

    with open(os.path.join(sfz_dir, f"{inst_name}.sfz"), "w") as f:
        f.write("\n".join(sfz_lines) + "\n")

    # Empacotamento Binário SF2
    def chunk(cid, payload):
        if len(payload) % 2 == 1:
            payload += b"\x00"
        return cid.encode("ascii") + struct.pack("<I", len(payload)) + payload

    def list_chunk(lt, sub):
        return chunk("LIST", lt.encode("ascii") + sub)

    info = (
        chunk("ifil", struct.pack("<HH", 2, 1)) +
        chunk("isng", b"EMU8000\x00") +
        chunk("INAM", inst_name.encode("ascii", "ignore")[:30] + b"\x00")
    )
    sdta = list_chunk("sdta", chunk("smpl", b"".join(smpl_parts)))

    phdr = struct.pack("<20sHHHIII", inst_name.encode("ascii", "ignore")[:19], 0, 0, 0, 0, 0, 0)
    phdr += struct.pack("<20sHHHIII", b"EOP", 0, 0, 1, 0, 0, 0)
    pbag = struct.pack("<HH", 0, 0) + struct.pack("<HH", 1, 0)
    pmod = struct.pack("<HHHHH", 0, 0, 0, 0, 0)
    pgen = struct.pack("<HH", 41, 0) + struct.pack("<HH", 0, 0)

    inst_b = struct.pack("<20sH", inst_name.encode("ascii", "ignore")[:19], 0)
    inst_b += struct.pack("<20sH", b"EOI", len(zones) * 2)

    ibag = b""
    igen = b""
    g_idx = 0
    for z_idx, (rk, lo, hi, sr, data, fname) in enumerate(zones):
        for ch_offset, pan_val in [(0, -500), (1, 500)]:
            s_idx = z_idx * 2 + ch_offset
            ibag += struct.pack("<HH", g_idx, 0)
            ZoneGens = [
                (43, (hi << 8) | lo),
                (34, timecents(0.001)),
                (36, timecents(80.0)),
                (37, 0),
                (38, timecents(rel_sec)),
                (17, pan_val & 0xFFFF),
                (54, 0), # 0 = SEM LOOP (no_loop)
                (58, rk),
                (53, s_idx)
            ]
            for op, val in ZoneGens:
                igen += struct.pack("<HH", op, val & 0xFFFF)
            g_idx += len(ZoneGens)

    ibag += struct.pack("<HH", g_idx, 0)
    igen += struct.pack("<HH", 0, 0)
    imod = struct.pack("<HHHHH", 0, 0, 0, 0, 0)

    shdr = b""
    for name, s, e, ls, le, sr, op, ch, link, stype in shdr_entries:
        shdr += struct.pack("<20sIIIIIBbHH", name.encode("ascii"), s, e, ls, le, sr, op, ch, link, stype)
    shdr += struct.pack("<20sIIIIIBbHH", b"EOS", 0, 0, 0, 0, 0, 0, 0, 0, 0)

    pdta = list_chunk("pdta",
        chunk("phdr", phdr) + chunk("pbag", pbag) + chunk("pmod", pmod) + chunk("pgen", pgen) +
        chunk("inst", inst_b) + chunk("ibag", ibag) + chunk("imod", imod) + chunk("igen", igen) +
        chunk("shdr", shdr)
    )
    riff = b"RIFF" + struct.pack("<I", 4 + len(list_chunk("INFO", info)) + len(sdta) + len(pdta)) + b"sfbk" + list_chunk("INFO", info) + sdta + pdta
    with open(sf2_path, "wb") as f:
        f.write(riff)
    print(f"␅ {inst_name}.sf2 gerado! [{tipo_str}] ({len(zones)} zonas = {len(zones)*2} amostras, {len(riff)/1024/1024:.1f} MB)")

if __name__ == "__main__":
    organize_loose_numbered_wavs()
    for d in sorted(glob.glob(os.path.join(SAMPLES_BASE, "*"))):
        if os.path.isdir(d):
            build_sf2_and_sfz(d)

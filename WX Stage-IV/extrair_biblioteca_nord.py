import os, sys, glob, re, base64, struct, time, shutil, subprocess, hashlib
import numpy as np
import soundfile as sf

SAMPLES_BASE = "/content/wxstage_samples"
OUT_BASE     = "/content/drive/MyDrive/AgentNexora/soundfonts_wxstage"
TEMPLATE_TXT = "/content/chunk_sample_template.txt"
PARAMS_TXT   = "/content/wxstage_params.txt"

os.makedirs(SAMPLES_BASE, exist_ok=True)
os.makedirs(OUT_BASE, exist_ok=True)

SUSTAIN_WORDS = [
    "lead", "synth", "str", "pad", "brass", "choir", "organ", "flute",
    "orch", "mellotron", "saw", "mini", "arp", "crumar", "elka", "wind",
    "horn", "ens", "accord", "harm", "melodica", "sax", "clar", "oboe",
    "violin", "viola", "cello", "voice", "men", "boys", "fem", "gamba", "principal"
]

def is_sustained(name_or_cat):
    low = name_or_cat.lower()
    if any(p in low for p in ["pizz", "slap", "plucked", "guitar", "percussion"]):
        return False
    return any(k in low for k in SUSTAIN_WORDS)

def clean_name(prefix, filename):
    base = re.sub(r"\.(npno|nsmp4|nsmp3|nsmp)$", "", filename, flags=re.IGNORECASE)
    base = re.sub(r"\s+\d+\.\d+$", "", base).strip()
    safe = re.sub(r"[^A-Za-z0-9_+.-]+", "_", f"{prefix}_{base}").strip("_")
    return safe[:42]

def load_base_chunk():
    src = TEMPLATE_TXT if os.path.exists(TEMPLATE_TXT) else PARAMS_TXT
    with open(src, "r", errors="ignore") as f:
        txt = f.read()
    if "=== TRACK_STATE_CHUNK ===" in txt:
        txt = txt.split("=== TRACK_STATE_CHUNK ===")[1].strip()
    return txt

def build_custom_chunk(base_chunk, mode, fname, win_path, is_sust):
    vst_m = re.search(r"(<VST\s+[^\n]+\n)(.*?)(\n\s*>)", base_chunk, re.DOTALL)
    if not vst_m:
        raise RuntimeError("Bloco <VST> nao encontrado no chunk base")

    header_line, body, footer_line = vst_m.group(1), vst_m.group(2), vst_m.group(3)
    lines = [l.strip() for l in body.splitlines() if l.strip()]

    b0 = base64.b64decode(lines[0])
    b1 = base64.b64decode("".join(lines[1:-1]))
    b2 = base64.b64decode(lines[-1])

    xml_start = b1.find(b"<?xml")
    xml_end   = b1.find(b"</NP>") + 5
    trailer   = b1[xml_end:]
    xml_str   = b1[xml_start:xml_end].decode("utf-8", errors="ignore")

    if mode == "npno":
        p_name = fname.encode("utf-8") + b"\x00"
        p_path = win_path.encode("utf-8") + b"\x00"
        flags  = b"\x01\x00\x00\x00\x00\x01"
        p_hdr  = p_name + p_path + flags + p_name + p_path + flags + b"\xcd\xccL?"

        xml_str = re.sub(r'sample="[^"]*"', 'sample=""', xml_str)
        xml_str = re.sub(r'samplePath="[^"]*"', 'samplePath=""', xml_str)
        xml_str = re.sub(r'(<PARAM id="p_on" value=")[^"]*("/>)', r'\g<1>1.0\2', xml_str)
        xml_str = re.sub(r'(<PARAM id="s_on" value=")[^"]*("/>)', r'\g<1>0.0\2', xml_str)
        xml_str = re.sub(r'(<PARAM id="o_on" value=")[^"]*("/>)', r'\g<1>0.0\2', xml_str)
    else:
        p_name = b"Grand__White Grand Sml 6.3.npno\x00"
        p_path = b"\x00"
        flags  = b"\x01\x00\x00\x00\x00\x01"
        p_hdr  = p_name + p_path + flags + p_name + p_path + flags + b"\xcd\xccL?"

        xml_str = re.sub(r'sample="[^"]*"', lambda _: f'sample="{fname}"', xml_str)
        xml_str = re.sub(r'samplePath="[^"]*"', lambda _: f'samplePath="{win_path}"', xml_str)
        xml_str = re.sub(r'(<PARAM id="p_on" value=")[^"]*("/>)', r'\g<1>0.0\2', xml_str)
        xml_str = re.sub(r'(<PARAM id="s_on" value=")[^"]*("/>)', r'\g<1>1.0\2', xml_str)
        xml_str = re.sub(r'(<PARAM id="o_on" value=")[^"]*("/>)', r'\g<1>0.0\2', xml_str)
        rel_val = "0.65" if is_sust else "0.45"
        xml_str = re.sub(r'(<PARAM id="s_ar" value=")[^"]*("/>)', f'\\g<1>{rel_val}\\2', xml_str)
        xml_str = re.sub(r'(<PARAM id="lb_s_ar" value=")[^"]*("/>)', f'\\g<1>{rel_val}\\2', xml_str)

    b1_payload = b"\x01\x00\x00\x001NPN\x03\x00\x00\x00" + p_hdr + xml_str.encode("utf-8") + trailer
    b1_new = struct.pack("<I", len(b1_payload) - 8) + b1_payload
    b0_new = b0[:32] + struct.pack("<I", len(b1_new)) + b0[36:]

    def wrap_b64(data):
        s = base64.b64encode(data).decode("ascii")
        return "\n".join("      " + s[i:i+128] for i in range(0, len(s), 128))

    new_vst = header_line + wrap_b64(b0_new) + "\n" + wrap_b64(b1_new) + "\n" + wrap_b64(b2) + footer_line
    return base_chunk[:vst_m.start()] + new_vst + base_chunk[vst_m.end():]

def write_reaper_batch_lua(batch_items, lua_path):
    lines = [
        'local NOTES = {',
        '  {21, "A0"},  {24, "C1"},  {27, "Eb1"}, {30, "Gb1"}, {32, "Ab1"}, {34, "Bb1"},',
        '  {36, "C2"},  {38, "D2"},  {40, "E2"},  {42, "Gb2"}, {44, "Ab2"}, {46, "Bb2"},',
        '  {48, "C3"},  {50, "D3"},  {52, "E3"},  {54, "Gb3"}, {56, "Ab3"}, {58, "Bb3"},',
        '  {60, "C4"},  {62, "D4"},  {64, "E4"},  {66, "Gb4"}, {68, "Ab4"}, {70, "Bb4"},',
        '  {72, "C5"},  {74, "D5"},  {76, "E5"},  {78, "Gb5"}, {80, "Ab5"}, {82, "Bb5"},',
        '  {84, "C6"},  {86, "D6"},  {88, "E6"},  {90, "Gb6"}, {93, "A6"},  {96, "C7"},',
        '  {100, "E7"}, {104, "Ab7"}',
        '}',
        '',
        'local function setup_timeline(track, note_len, tail_len)',
        '  local gap_len = 0.5',
        '  local step_len = note_len + tail_len + gap_len',
        '  local _, num_markers, num_regions = reaper.CountProjectMarkers(0)',
        '  for i = num_markers + num_regions - 1, 0, -1 do',
        '    local _, isrgn, _, _, _, idx = reaper.EnumProjectMarkers(i)',
        '    reaper.DeleteProjectMarker(0, idx, isrgn)',
        '  end',
        '  while reaper.CountTrackMediaItems(track) > 0 do',
        '    reaper.DeleteTrackMediaItem(track, reaper.GetTrackMediaItem(track, 0))',
        '  end',
        '  local total_len = #NOTES * step_len',
        '  local item = reaper.CreateNewMIDIItemInProj(track, 0, total_len, false)',
        '  local take = reaper.GetActiveTake(item)',
        '  for idx, n in ipairs(NOTES) do',
        '    local pitch, name = n[1], n[2]',
        '    local start_sec = (idx - 1) * step_len',
        '    local note_end  = start_sec + note_len',
        '    local rgn_end   = start_sec + note_len + tail_len',
        '    local rgn_name  = string.format("%03d_%s", pitch, name)',
        '    reaper.AddProjectMarker2(0, true, start_sec, rgn_end, rgn_name, idx, 0)',
        '    local sp = reaper.MIDI_GetPPQPosFromProjTime(take, start_sec)',
        '    local ep = reaper.MIDI_GetPPQPosFromProjTime(take, note_end)',
        '    reaper.MIDI_InsertNote(take, false, false, sp, ep, 0, pitch, 110, true)',
        '  end',
        '  reaper.MIDI_Sort(take)',
        'end',
        '',
        'reaper.Main_OnCommand(40029, 0)',
        'while reaper.GetNumTracks() > 0 do reaper.DeleteTrack(reaper.GetTrack(0, 0)) end',
        'reaper.GetSetProjectInfo(0, "PROJECT_SRATE", 48000, true)',
        'reaper.GetSetProjectInfo(0, "PROJECT_SRATE_USE", 1, true)',
        'reaper.SetCurrentBPM(0, 120, false)',
        'reaper.InsertTrackAtIndex(0, false)',
        'local track = reaper.GetTrack(0, 0)',
        '',
        'reaper.GetSetProjectInfo(0, "RENDER_SETTINGS", 0, true)',
        'reaper.GetSetProjectInfo(0, "RENDER_BOUNDSFLAG", 3, true)',
        'reaper.GetSetProjectInfo(0, "RENDER_SRATE", 48000, true)',
        'reaper.GetSetProjectInfo(0, "RENDER_CHANNELS", 2, true)',
        'reaper.GetSetProjectInfo(0, "RENDER_ADDTOPROJ", 0, true)',
        'reaper.GetSetProjectInfo_String(0, "RENDER_PATTERN", "$region", true)',
        ''
    ]

    for idx, (inst_name, chunk_file, is_sust) in enumerate(batch_items):
        win_chunk = "Z:" + chunk_file.replace("/", "\\")
        note_l = 8.5 if is_sust else 6.0
        tail_l = 2.5 if is_sust else 1.5
        lines += [
            f'-- Instrumento {idx+1}: {inst_name}',
            f'local cf = io.open({win_chunk!r}, "r")',
            'if cf then',
            '  local chunk_data = cf:read("*all")',
            '  cf:close()',
            '  reaper.SetTrackStateChunk(track, chunk_data, false)',
            f'  setup_timeline(track, {note_l}, {tail_l})',
            f'  reaper.GetSetProjectInfo_String(0, "RENDER_FILE", "Z:\\\\content\\\\wxstage_samples\\\\{inst_name}", true)',
            '  reaper.Main_OnCommand(42230, 0)',
            'end',
            ''
        ]

    lines += [
        'local df = io.open("Z:\\\\content\\\\batch_done.txt", "w")',
        'if df then df:write("OK\\n"); df:close() end'
    ]
    with open(lua_path, "w") as f:
        f.write("\n".join(lines) + "\n")

def run_reaper_batch(batch_items):
    os.makedirs("/content/batch_chunks", exist_ok=True)
    base_chunk = load_base_chunk()
    lua_entries = []
    for idx, item in enumerate(batch_items):
        inst_name, mode, fname, win_path, is_sust = item
        c_str = build_custom_chunk(base_chunk, mode, fname, win_path, is_sust)
        c_path = f"/content/batch_chunks/chunk_{idx}.txt"
        with open(c_path, "w") as f:
            f.write(c_str)
        lua_entries.append((inst_name, c_path, is_sust))

    lua_path = "/content/run_batch.lua"
    write_reaper_batch_lua(lua_entries, lua_path)
    if os.path.exists("/content/batch_done.txt"):
        os.remove("/content/batch_done.txt")

    subprocess.run("pkill -f reaper.exe 2>/dev/null || true", shell=True)
    time.sleep(1)
    cmd = 'nohup /usr/bin/wine "C:\\Program Files\\REAPER (x64)\\reaper.exe" "Z:\\content\\run_batch.lua" >/content/reaper_batch.log 2>&1 &'
    subprocess.run(cmd, shell=True)

    expected_wavs = len(batch_items) * 38
    t0 = time.time()
    while time.time() - t0 < 200:
        if os.path.exists("/content/batch_done.txt"):
            break
        done_wavs = 0
        for inst_name, _, _ in lua_entries:
            done_wavs += len(glob.glob(f"/content/wxstage_samples/{inst_name}/*.wav"))
        print(f"  [{int(time.time()-t0)}s] Renderizando lote ({len(batch_items)} instr.): {done_wavs}/{expected_wavs} WAVs...", flush=True)
        time.sleep(3)

    subprocess.run("pkill -f reaper.exe 2>/dev/null || true", shell=True)

def audio_signature(wav_path):
    if not os.path.exists(wav_path):
        return (0.0, "")
    data, _ = sf.read(wav_path, dtype="float32", always_2d=True)
    peak = float(np.max(np.abs(data)))
    norm = (data[:20000, 0] / max(peak, 1e-6) * 1000).astype("<i2").tobytes()
    return (peak, hashlib.md5(norm).hexdigest()[:10])

def main():
    npnos = sorted(glob.glob("/content/nord_library/unpacked/**/*.npno", recursive=True))
    nsmps = sorted(glob.glob("/content/nord_library/unpacked/**/*.nsmp4", recursive=True))

    queue = []
    for p in npnos:
        cat = os.path.basename(os.path.dirname(p))
        fn  = os.path.basename(p)
        iname = clean_name(f"Pno_{cat}", fn)
        wpath = p.replace("/content/nord_library", "C:\\Nord_Library").replace("/", "\\")
        queue.append((iname, "npno", fn, wpath, is_sustained(cat + " " + fn)))

    for s in nsmps:
        cat = os.path.basename(os.path.dirname(s))
        fn  = os.path.basename(s)
        iname = clean_name(f"Smp_{cat}", fn)
        wpath = s.replace("/content/nord_library", "C:\\Nord_Library").replace("/", "\\")
        queue.append((iname, "nsmp4", fn, wpath, is_sustained(cat + " " + fn)))

    pending = [q for q in queue if not os.path.exists(os.path.join(OUT_BASE, f"{q[0]}.sf2"))]
    print(f"=== FILA TOTAL: {len(queue)} instrumentos | Pendentes: {len(pending)} ===")

    test_npno = next((q for q in pending if "Royal_Grand" in q[0]), pending[0])
    test_nsmp = next((q for q in pending if "ARP_Quadra" in q[0]), pending[-1])
    test_batch = [test_npno, test_nsmp]

    print("\n=== [TESTE DE VERIFICAÇÃO] Testando injeção de 1 .npno e 1 .nsmp4 ===")
    for t in test_batch:
        print(f"  -> {t[0]} ({t[2]}) | Sustentado/Release Longo={t[4]}")

    run_reaper_batch(test_batch)

    ref_peak, ref_hash = audio_signature("/content/wxstage_samples/01_White_Grand/060_C4.wav")
    print(f"  Assinatura referência (01_White_Grand C4): peak={ref_peak:.4f}, hash={ref_hash}")

    ok_count = 0
    for t in test_batch:
        w60 = f"/content/wxstage_samples/{t[0]}/060_C4.wav"
        pk, hsh = audio_signature(w60)
        dur = sf.info(w60).duration if os.path.exists(w60) else 0.0
        print(f"  Resultado {t[0]}: dur={dur:.1f}s, peak={pk:.4f}, hash={hsh}")
        if pk > 0.005 and (hsh != ref_hash or ref_hash == ""):
            ok_count += 1

    if ok_count < len(test_batch):
        print("\n ␏ Atenção: algum instrumento de teste ficou em silêncio ou igual ao White Grand.")
        return

    print("\n␅ Verificação aprovada! Convertendo lote de teste para .SF2/.SFZ...")
    subprocess.run(["python3", "/content/gerar_sf2_sfz_76samples.py"])
    for t in test_batch:
        shutil.rmtree(f"/content/wxstage_samples/{t[0]}", ignore_errors=True)

    remaining = [q for q in pending if q not in test_batch]
    BATCH_SIZE = 4
    for i in range(0, len(remaining), BATCH_SIZE):
        batch = remaining[i:i+BATCH_SIZE]
        print(f"\n=== LOTE {(i//BATCH_SIZE)+1}/{(len(remaining)+BATCH_SIZE-1)//BATCH_SIZE}: {[b[0] for b in batch]} ===")
        run_reaper_batch(batch)
        subprocess.run(["python3", "/content/gerar_sf2_sfz_76samples.py"])
        for b in batch:
            if os.path.exists(os.path.join(OUT_BASE, f"{b[0]}.sf2")):
                shutil.rmtree(f"/content/wxstage_samples/{b[0]}", ignore_errors=True)

if __name__ == "__main__":
    main()

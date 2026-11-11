-- Auto-Extrator em Lote dos 6 Instrumentos Nativos Restantes + Captura do Molde .nsmp4
local NOTES = {
  {21, "A0"},  {24, "C1"},  {27, "Eb1"}, {30, "Gb1"}, {32, "Ab1"}, {34, "Bb1"},
  {36, "C2"},  {38, "D2"},  {40, "E2"},  {42, "Gb2"}, {44, "Ab2"}, {46, "Bb2"},
  {48, "C3"},  {50, "D3"},  {52, "E3"},  {54, "Gb3"}, {56, "Ab3"}, {58, "Bb3"},
  {60, "C4"},  {62, "D4"},  {64, "E4"},  {66, "Gb4"}, {68, "Ab4"}, {70, "Bb4"},
  {72, "C5"},  {74, "D5"},  {76, "E5"},  {78, "Gb5"}, {80, "Ab5"}, {82, "Bb5"},
  {84, "C6"},  {86, "D6"},  {88, "E6"},  {90, "Gb6"}, {93, "A6"},  {96, "C7"},
  {100, "E7"}, {104, "Ab7"}
}

local NOTE_LEN = 6.0
local TAIL_LEN = 1.5
local GAP_LEN  = 0.5
local STEP_LEN = NOTE_LEN + TAIL_LEN + GAP_LEN

reaper.Main_OnCommand(40029, 0)
while reaper.GetNumTracks() > 0 do
  reaper.DeleteTrack(reaper.GetTrack(0, 0))
end

local _, num_markers, num_regions = reaper.CountProjectMarkers(0)
for i = num_markers + num_regions - 1, 0, -1 do
  local _, isrgn, _, _, _, markrgnindexnumber = reaper.EnumProjectMarkers(i)
  reaper.DeleteProjectMarker(0, markrgnindexnumber, isrgn)
end

reaper.GetSetProjectInfo(0, "PROJECT_SRATE", 48000, true)
reaper.GetSetProjectInfo(0, "PROJECT_SRATE_USE", 1, true)
reaper.SetCurrentBPM(0, 120, false)

reaper.InsertTrackAtIndex(0, false)
local track = reaper.GetTrack(0, 0)
reaper.GetSetMediaTrackInfo_String(track, "P_NAME", "WX Stage-IV", true)
local fx = reaper.TrackFX_AddByName(track, "WX Stage-IV", false, 1)

local total_len = #NOTES * STEP_LEN
local item = reaper.CreateNewMIDIItemInProj(track, 0, total_len, false)
local take = reaper.GetActiveTake(item)

for idx, n in ipairs(NOTES) do
  local pitch = n[1]
  local name  = n[2]
  local start_sec = (idx - 1) * STEP_LEN
  local note_end  = start_sec + NOTE_LEN
  local rgn_end   = start_sec + NOTE_LEN + TAIL_LEN
  local rgn_name  = string.format("%03d_%s", pitch, name)

  reaper.AddProjectMarker2(0, true, start_sec, rgn_end, rgn_name, idx, 0)

  local start_ppq = reaper.MIDI_GetPPQPosFromProjTime(take, start_sec)
  local end_ppq   = reaper.MIDI_GetPPQPosFromProjTime(take, note_end)
  reaper.MIDI_InsertNote(take, false, false, start_ppq, end_ppq, 0, pitch, 110, true)
end
reaper.MIDI_Sort(take)

-- Configurar Render: 0 = Master Mix, 3 = All Project Regions ($region), 48kHz Stereo
reaper.GetSetProjectInfo(0, "RENDER_SETTINGS", 0, true)
reaper.GetSetProjectInfo(0, "RENDER_BOUNDSFLAG", 3, true)
reaper.GetSetProjectInfo(0, "RENDER_SRATE", 48000, true)
reaper.GetSetProjectInfo(0, "RENDER_CHANNELS", 2, true)
reaper.GetSetProjectInfo(0, "RENDER_ADDTOPROJ", 0, true)
reaper.GetSetProjectInfo_String(0, "RENDER_PATTERN", "$region", true)

local _, num_presets = reaper.TrackFX_GetPresetIndex(track, fx)
local logf = io.open("Z:\\content\\presets_log.txt", "w")
logf:write("NUM_PRESETS=" .. tostring(num_presets) .. "\n")

-- Passo A: Capturar imediatamente o molde de Sample (.nsmp4) antes de renderizar
for p = 0, num_presets - 1 do
  reaper.TrackFX_SetPresetByIndex(track, fx, p)
  local _, pname = reaper.TrackFX_GetPreset(track, fx, "")
  if pname == "Bright Piano 1 4.12" then
    local _, chunk = reaper.GetTrackStateChunk(track, "", false)
    local cf = io.open("Z:\\content\\chunk_sample_template.txt", "w")
    if cf then
      cf:write(chunk)
      cf:close()
    end
    break
  end
end

-- Passo B: Renderizar em lote os 6 instrumentos nativos restantes
local TARGETS = {
  ["EP1 Deep Timbre Sml 6.1"]            = "02_EP1_Deep_Timbre",
  ["Silver EP Sml 5.2"]                  = "03_Silver_EP",
  ["Beethoven ForteP Broadwood Sml 5.3"] = "04_Beethoven_ForteP",
  ["Bright Piano 1 4.12"]                = "05_Bright_Piano_1",
  ["Bright Piano 2 4.14"]                = "06_Bright_Piano_2",
  ["Toy Piano LA 4.12"]                  = "07_Toy_Piano_LA"
}

for p = 0, num_presets - 1 do
  reaper.TrackFX_SetPresetByIndex(track, fx, p)
  local _, pname = reaper.TrackFX_GetPreset(track, fx, "")
  logf:write(string.format("PRESET[%d] = %s\n", p, pname))
  logf:flush()

  local folder = TARGETS[pname]
  if folder then
    local out_dir = "Z:\\content\\wxstage_samples\\" .. folder
    reaper.GetSetProjectInfo_String(0, "RENDER_FILE", out_dir, true)
    reaper.Main_OnCommand(42230, 0)
  end
end

logf:write("DONE\n")
logf:close()

local donef = io.open("Z:\\content\\render_6_done.txt", "w")
if donef then
  donef:write("OK\n")
  donef:close()
end

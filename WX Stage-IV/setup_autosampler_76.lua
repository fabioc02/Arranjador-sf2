reaper.Main_OnCommand(40023, 0)
reaper.SetCurrentBPM(0, 120, false)

reaper.InsertTrackAtIndex(0, true)
local track = reaper.GetTrack(0, 0)
reaper.GetSetMediaTrackInfo_String(track, "P_NAME", "WX Stage-IV", true)

local fx_idx = reaper.TrackFX_AddByName(track, "WX Stage-IV", false, -1)
if fx_idx >= 0 then
    reaper.TrackFX_Show(track, fx_idx, 3)
end

local pitches = {
    21, 24, 27, 30, 32, 34, 36, 38, 40, 42,
    44, 46, 48, 50, 52, 54, 56, 58, 60, 62,
    64, 66, 68, 70, 72, 74, 76, 78, 80, 82,
    84, 86, 88, 90, 93, 96, 100, 104
}

local note_names = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"}
local step_time = 8.0
local note_dur  = 6.0
local reg_dur   = 7.5

for i, pitch in ipairs(pitches) do
    local idx = i - 1
    local octave = math.floor(pitch / 12) - 1
    local nname = note_names[(pitch % 12) + 1] .. tostring(octave)
    local reg_name = string.format("%03d_%s", pitch, nname)

    local t_start = idx * step_time
    local t_end   = t_start + reg_dur

    reaper.AddProjectMarker2(0, true, t_start, t_end, reg_name, i, 0)

    local item = reaper.CreateNewMIDIItemInProj(track, t_start, t_end, false)
    local take = reaper.GetActiveTake(item)
    local ppq_start = reaper.MIDI_GetPPQPosFromProjTime(take, t_start)
    local ppq_end   = reaper.MIDI_GetPPQPosFromProjTime(take, t_start + note_dur)
    reaper.MIDI_InsertNote(take, false, false, ppq_start, ppq_end, 0, pitch, 118, false)
    reaper.MIDI_Sort(take)
end

reaper.GetSetProjectInfo(0, "RENDER_SETTINGS", 0, true)
reaper.GetSetProjectInfo(0, "RENDER_BOUNDSFLAG", 3, true)
reaper.GetSetProjectInfo(0, "RENDER_SRATE", 48000, true)
reaper.GetSetProjectInfo(0, "RENDER_CHANNELS", 2, true)
reaper.GetSetProjectInfo_String(0, "RENDER_FILE", "Z:\\content\\wxstage_samples\\01_White_Grand", true)
reaper.GetSetProjectInfo_String(0, "RENDER_PATTERN", "$region", true)

-- Salvar todos os parâmetros e State Chunk do WX Stage-IV em /content/wxstage_params.txt
if fx_idx >= 0 then
    local f = io.open("Z:\\content\\wxstage_params.txt", "w")
    if f then
        local num_params = reaper.TrackFX_GetNumParams(track, fx_idx)
        f:write("TOTAL_PARAMS=" .. tostring(num_params) .. "\n")
        for p = 0, num_params - 1 do
            local _, pname = reaper.TrackFX_GetParamName(track, fx_idx, p, "")
            local val = reaper.TrackFX_GetParam(track, fx_idx, p)
            f:write(string.format("PARAM[%d] = %s (val=%.4f)\n", p, pname, val))
        end
        local _, chunk = reaper.GetTrackStateChunk(track, "", false)
        f:write("\n=== TRACK_STATE_CHUNK ===\n")
        f:write(chunk)
        f:close()
    end
end

reaper.TrackList_AdjustWindows(false)
reaper.UpdateArrange()
reaper.Main_OnCommand(40044, 0)

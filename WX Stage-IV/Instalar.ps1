# Instala o WX Stage-IV (NEW MODELS) no Windows 10/11 x64.
#  - VST3 em C:\Program Files\Common Files\VST3
#  - Standalone em C:\Program Files\NEW MODELS\WX Stage-IV (+ atalho no Menu Iniciar e na Area de Trabalho)
#  - Bancos em %APPDATA%\WX Stage-IV (arquivos ja existentes sao mantidos: sao do usuario)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$appData = if ($args.Count -gt 0) { $args[0] } else { $env:APPDATA }

$vst3Dir = Join-Path $env:CommonProgramFiles 'VST3'
$vst3 = Join-Path $vst3Dir 'WX Stage-IV.vst3'
New-Item -ItemType Directory -Force -Path $vst3Dir | Out-Null
if (Test-Path $vst3) { Remove-Item -Recurse -Force $vst3 }
Copy-Item -Recurse -Force (Join-Path $here 'VST3\WX Stage-IV.vst3') $vst3Dir
# The folder icon (Plugin.ico): desktop.ini hidden+system, the folder system.
$ini = Join-Path $vst3 'desktop.ini'
if (Test-Path $ini) {
    $i = Get-Item -Force $ini
    $i.Attributes = $i.Attributes -bor [IO.FileAttributes]::Hidden -bor [IO.FileAttributes]::System
    $b = Get-Item -Force $vst3
    $b.Attributes = $b.Attributes -bor [IO.FileAttributes]::System
}

$appDir = Join-Path $env:ProgramFiles 'NEW MODELS\WX Stage-IV'
New-Item -ItemType Directory -Force -Path $appDir | Out-Null
Copy-Item -Force (Join-Path $here 'Standalone\WX Stage-IV.exe') $appDir
$exe = Join-Path $appDir 'WX Stage-IV.exe'
$sh = New-Object -ComObject WScript.Shell
foreach ($lnkDir in @([Environment]::GetFolderPath('CommonPrograms'), [Environment]::GetFolderPath('CommonDesktopDirectory'))) {
    $l = $sh.CreateShortcut((Join-Path $lnkDir 'WX Stage-IV.lnk')); $l.TargetPath = $exe; $l.WorkingDirectory = $appDir; $l.IconLocation = "$exe,0"; $l.Save()
}

$src = Join-Path $here 'Bancos'
$dst = Join-Path $appData 'WX Stage-IV'
New-Item -ItemType Directory -Force -Path $dst | Out-Null
if (Test-Path $src) {
    Get-ChildItem -Recurse -File $src | ForEach-Object {
        $rel = $_.FullName.Substring($src.Length).TrimStart('\')
        $to = Join-Path $dst $rel
        if (-not (Test-Path $to)) { New-Item -ItemType Directory -Force -Path (Split-Path $to) | Out-Null; Copy-Item $_.FullName $to }
    }
}
Write-Host ''
Write-Host 'WX Stage-IV instalado.'
Write-Host "  VST3:       $vst3"
Write-Host "  Standalone: $exe"
Write-Host "  Bancos:     $dst"

<#
.SYNOPSIS
  Generates the phase 5 synthetic TTS WAV files listed in tts-manifest.json (Windows only, System.Speech).

.DESCRIPTION
  Each manifest entry is spoken by the installed Windows voice into a 16 kHz, 16-bit, mono PCM WAV (the format
  the Azure pronunciation assessment expects). "[pause N]" markers become SSML breaks of N milliseconds; the
  fillers ("uh", "um") and repeated words are spoken as written. The WAV files are written to the output folder
  and must never be committed. Synthetic audio proves only that Oral Fluency moves in the right direction; it
  says nothing about scoring accuracy.

.PARAMETER OutputDir
  Target folder; defaults to $env:SCORING_AI_SPIKE_WAV_DIR.

.EXAMPLE
  $env:SCORING_AI_SPIKE_WAV_DIR = 'D:\spike\wav'
  powershell -ExecutionPolicy Bypass -File .\generate-tts.ps1
#>
param(
    [string]$OutputDir = $env:SCORING_AI_SPIKE_WAV_DIR,
    [string]$Manifest = (Join-Path $PSScriptRoot 'tts-manifest.json')
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    throw 'Set SCORING_AI_SPIKE_WAV_DIR or pass -OutputDir.'
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

Add-Type -AssemblyName System.Speech
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000,
    [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
$entries = (Get-Content -Raw -Encoding UTF8 $Manifest | ConvertFrom-Json).entries

function ConvertTo-Ssml([string]$text) {
    $escaped = [System.Security.SecurityElement]::Escape($text)
    $withBreaks = [regex]::Replace($escaped, '\[pause (\d+)\]', '<break time="$1ms"/>')
    return '<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="en-US">' +
        $withBreaks + '</speak>'
}

foreach ($entry in $entries) {
    $target = Join-Path $OutputDir $entry.fileName
    $synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
    try {
        $synth.SetOutputToWaveFile($target, $format)
        $synth.SpeakSsml((ConvertTo-Ssml $entry.text))
    } finally {
        $synth.Dispose()
    }
    Write-Output ('{0} ({1}, from {2})' -f $entry.fileName, $entry.variant, $entry.basedOnSampleId)
}

[CmdletBinding()]
param(
    [string]$ContentPath,
    [string]$OutDir
)

# Generates the self-authored demo media for the 4 task types that have no
# usable source audio/image (Windows only: System.Speech + Python Pillow):
#   demo22-<key>.wav  FILL_IN_THE_BLANKS_TYPE_IN, HIGHLIGHT_INCORRECT_WORDS,
#                     SUMMARIZE_GROUP_DISCUSSION
#   demo22-<key>.png  DESCRIBE_IMAGE
# Highlight Incorrect Words is recorded from the ORIGINAL text; the question
# later displays the altered text, so the differences are what the student
# must find.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Speech

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($ContentPath)) {
    $ContentPath = Join-Path $scriptDir "..\data\gap-content.json"
}
if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $OutDir = Join-Path $env:TEMP "demo22-media"
}

$content = Get-Content -Raw -Path $ContentPath | ConvertFrom-Json
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
    22050, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
    [System.Speech.AudioFormat.AudioChannel]::Mono)

function Save-Speech {
    param([string]$Path, [System.Speech.Synthesis.PromptBuilder]$Prompt)
    $synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
    try {
        $synth.SetOutputToWaveFile($Path, $format)
        $synth.Speak($Prompt)
    }
    finally {
        $synth.Dispose()
    }
}

function New-SinglePrompt {
    param([string]$Text, [string]$Voice = "Microsoft David Desktop")
    $prompt = New-Object System.Speech.Synthesis.PromptBuilder
    $prompt.StartVoice($Voice)
    $prompt.AppendText($Text)
    $prompt.EndVoice()
    return $prompt
}

foreach ($item in $content.fillInTheBlanksTypeIn) {
    Save-Speech -Path (Join-Path $OutDir "demo22-$($item.key).wav") -Prompt (New-SinglePrompt -Text $item.text)
}
foreach ($item in $content.highlightIncorrectWords) {
    Save-Speech -Path (Join-Path $OutDir "demo22-$($item.key).wav") -Prompt (New-SinglePrompt -Text $item.text -Voice "Microsoft Zira Desktop")
}

# Three speakers on two installed voices: C reuses David at a slower pace.
$speakers = @{
    A = @{ Voice = "Microsoft David Desktop"; Rate = [System.Speech.Synthesis.PromptRate]::Medium }
    B = @{ Voice = "Microsoft Zira Desktop"; Rate = [System.Speech.Synthesis.PromptRate]::Medium }
    C = @{ Voice = "Microsoft David Desktop"; Rate = [System.Speech.Synthesis.PromptRate]::Slow }
}
foreach ($item in $content.summarizeGroupDiscussion) {
    $prompt = New-Object System.Speech.Synthesis.PromptBuilder
    foreach ($turn in $item.turns) {
        $speaker = $speakers[$turn[0]]
        $style = New-Object System.Speech.Synthesis.PromptStyle($speaker.Rate)
        $prompt.StartVoice($speaker.Voice)
        $prompt.StartStyle($style)
        $prompt.AppendText($turn[1])
        $prompt.EndStyle()
        $prompt.EndVoice()
        $prompt.AppendBreak([TimeSpan]::FromMilliseconds(700))
    }
    Save-Speech -Path (Join-Path $OutDir "demo22-$($item.key).wav") -Prompt $prompt
}

$python = Join-Path $scriptDir "generate_demo22_images.py"
& python $python --content $ContentPath --out $OutDir
if ($LASTEXITCODE -ne 0) {
    throw "Image generation failed (python exit code $LASTEXITCODE). Pillow is required: pip install pillow"
}

Get-ChildItem $OutDir -File | Sort-Object Name | ForEach-Object {
    "{0,-28} {1,10:N0} bytes" -f $_.Name, $_.Length
}

param([switch]$Force)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$jar  = Join-Path $root 'build\libs\sciencepacks-1.0.0-1.21.neo.jar'
if (-not (Test-Path $jar)) { throw "jar not found: $jar" }

$targets = @(
    'D:\PCL2\.minecraft\versions\1.21.1-NeoForge_21.1.265\mods',
    'E:\DeepSeek文件\我的世界Mod\03-交付产物'
)

if (-not $Force) {
    $mc = Get-Process javaw, java -ErrorAction SilentlyContinue |
          Where-Object { $_.MainWindowTitle -like '*Minecraft*' }
    if ($mc) {
        Write-Host 'Minecraft is running - close the game before replacing the jar (or pass -Force).'
        exit 1
    }
}

foreach ($t in $targets) {
    New-Item -ItemType Directory -Force -Path $t | Out-Null
    Copy-Item $jar (Join-Path $t (Split-Path -Leaf $jar)) -Force
    Write-Host "deployed -> $t"
}
Write-Host ('SHA256 ' + (Get-FileHash $jar -Algorithm SHA256).Hash)
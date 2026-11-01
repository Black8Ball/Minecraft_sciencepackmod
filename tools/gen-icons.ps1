# 从本地客户端 jar 与模组 jar 里导出物品图标，供科技树编辑器（tech-editor.html）显示
# 用法：pwsh -NoProfile -ExecutionPolicy Bypass -File tools\gen-icons.ps1
# 输出：tools\icons\<命名空间>\<物品路径>.png
param(
    [string]$Root = '',
    [string]$ClientJar = 'D:\PCL2\.minecraft\versions\1.21.1\1.21.1.jar',
    [string]$ModsDir = 'D:\PCL2\.minecraft\versions\1.21.1-NeoForge_21.1.265\mods'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = if ($Root) { $Root } elseif ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { Get-Location }
$techDir = Join-Path $root 'src\main\resources\data\sciencepacks\sciencepacks\technologies'
$outDir = Join-Path $root 'tools\icons'
$selfJar = Get-ChildItem (Join-Path $root 'build\libs') -Filter '*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*sources*' } | Select-Object -First 1

$jars = @()
if (Test-Path $ClientJar) { $jars += $ClientJar }
if ($selfJar) { $jars += $selfJar.FullName }
if (Test-Path $ModsDir) {
    $jars += (Get-ChildItem $ModsDir -Filter '*.jar' | ForEach-Object { $_.FullName })
}

# 索引所有 jar 里的 png：key = "ns:path"，value = @(jar, entry)
$index = @{}
$jarCount = 0
foreach ($jar in $jars) {
    $zip = $null
    try { $zip = [System.IO.Compression.ZipFile]::OpenRead($jar) } catch { continue }
    $jarCount++
    foreach ($entry in $zip.Entries) {
        $name = $entry.FullName
        if ($name -notlike 'assets/*/textures/*.png') { continue }
        $parts = $name -split '/', 4
        if ($parts.Count -lt 4) { continue }
        $ns = $parts[1]
        $rel = $parts[3] -replace '\.png$', ''
        $key = $ns + ':' + $rel
        if (-not $index.ContainsKey($key)) { $index[$key] = @($jar, $name) }
    }
    $zip.Dispose()
}
Write-Host "jars indexed: $jarCount, textures: $($index.Count)"

# 需要图标的物品：科技 JSON 里出现的 icon / unlocks / cost，加上导出过的物品清单
$items = @()
function Normalize-Id([string]$raw) {
    if (!$raw) { return $null }
    $value = ($raw -replace '^recipe:', '' -replace '^item:', '').Trim()
    if ($value -match '^[a-z0-9_.-]+:[a-z0-9_./-]+$') { return $value }
    return $null
}
$rawIds = @()
if (Test-Path $techDir) {
    foreach ($file in Get-ChildItem $techDir -Filter '*.json') {
        $json = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8) | ConvertFrom-Json
        $rawIds += $json.icon
        foreach ($u in $json.unlocks) { $rawIds += $u }
        foreach ($c in $json.cost) { $rawIds += $c.item }
    }
}
$dump = Join-Path $root 'run\config\sciencepacks-items.json'
if (Test-Path $dump) {
    $rawIds += ([IO.File]::ReadAllText($dump, [Text.Encoding]::UTF8) | ConvertFrom-Json)
}
foreach ($rawId in ($rawIds | Sort-Object -Unique)) {
    if (!$rawId) { continue }
    $clean = ($rawId -replace '^recipe:', '' -replace '^item:', '').Trim()
    if ($clean -match '^[a-z0-9_.-]+:[a-z0-9_./-]+$') {
        if ($items -notcontains $clean) { $items = @($items) + $clean }
    }
}
Write-Host "root=$root techDir=$techDir exists=$(Test-Path $techDir) raw=$($rawIds.Count)"; Write-Host "items to resolve: $($items.Count)"

$written = 0
$missing = 0
$debugLeft = 3
foreach ($id in $items) {
    if (!$id) { continue }
    $idx = $id.IndexOf(':')
    if ($idx -lt 1) { continue }
    $ns = $id.Substring(0, $idx)
    $path = $id.Substring($idx + 1)
    $hit = $null
    foreach ($cand in @("${ns}:item/$path", "${ns}:block/$path")) {
        if ($index.ContainsKey($cand)) { $hit = $index[$cand]; break }
    }
    if ($debugLeft -gt 0) {
        $probe = "${ns}:item/$path"
        Write-Host "DBG id=$id probe=$probe contains=$($index.ContainsKey($probe)) indexCount=$($index.Count)"
        $debugLeft--
    }
    if (-not $hit) { $missing++; if ($missing -le 25) { Write-Host "MISS $id" }; continue }
    $dir = Join-Path $outDir $ns
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $target = Join-Path $dir ($path + '.png')
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    $zip = [System.IO.Compression.ZipFile]::OpenRead($hit[0])
    try {
        $entry = $zip.GetEntry($hit[1])
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
        $written++
    } finally { $zip.Dispose() }
}
Write-Host "icons written: $written, unresolved: $missing"
Write-Host "output: $outDir"

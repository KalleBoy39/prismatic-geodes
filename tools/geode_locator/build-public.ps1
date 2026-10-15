# Builds the PUBLIC Prismatic Geode Locator (Windows x64 app image + ZIP) from the shared sources only.
# Optional local developer extensions (src-private, web-private) and a local locator.properties are never included.
#
# Usage: powershell -ExecutionPolicy Bypass -File build-public.ps1 -Jdk <JDK 25 with jlink support> -Out <folder>
#   -Jdk     a full JDK 25 (e.g. Eclipse Temurin 25); only used here to compile and to make the bundled runtime
#   -Out     where "Prismatic Geode Locator" (app image) and the ZIP are written
#   -ModJar  the Prismatic Geodes jar to bundle (default: release\ if present, else build\libs\ of the main project)
#   -Docs    the repository root (default): LICENSE, CREDITS.md, THIRD-PARTY-NOTICES.md, docs\TROUBLESHOOTING.md and
#            docs\locator\README.txt are copied next to the exe. Pass -Docs '' to skip.
# Compile-time only inputs (never shipped) are downloaded once into runtime\build-deps and checked against pinned
# hashes: the Minecraft 26.3 server (Mojang; its bundled libraries), Fabric Loader and Fabric API (FabricMC).
param([Parameter(Mandatory)][string]$Jdk, [Parameter(Mandatory)][string]$Out, [string]$ModJar, [string]$Docs,
      [string]$Version = '1.0.0')
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = $PSScriptRoot
$project = (Resolve-Path "$root\..\..").Path
if (-not $PSBoundParameters.ContainsKey('Docs')) { $Docs = $project }
$name = 'Prismatic Geode Locator'
$build = "$root\runtime\public-build"
if (-not $ModJar) {
    $ModJar = "$project\release\prismatic-geodes-1.0.0+26.3.jar"
    if (-not (Test-Path $ModJar)) { $ModJar = "$project\build\libs\prismatic-geodes-1.0.0+26.3.jar" }
}
if (-not (Test-Path $ModJar)) { throw "Prismatic Geodes jar not found ($ModJar): run gradlew build in the repository root first" }
Remove-Item -Recurse -Force $build -ErrorAction SilentlyContinue
# app\lib holds jars that must NOT be on the launcher's class path (jpackage adds every top-level jar of app\)
New-Item -ItemType Directory -Force "$build\core", "$build\launcher", "$build\probe", "$build\nested", "$build\app\web", "$build\app\lib" | Out-Null

function Run($exe, [string[]]$a) { & $exe @a; if ($LASTEXITCODE) { throw "$exe failed ($LASTEXITCODE)" } }
function ArgFile($file, $cp, $dest) {
    Set-Content -Encoding ascii $file ("-cp `"$($cp -replace '\\','/')`" -nowarn -encoding UTF-8 --release 25 -d `"$($dest -replace '\\','/')`"")
}
function Hash($file, $algo) { (Get-FileHash -Algorithm $algo $file).Hash.ToLower() }
function Fetch($url, $file, $algo, $hash) {
    if ((Test-Path $file) -and (Hash $file $algo) -eq $hash) { return }
    Write-Host "downloading $url"
    New-Item -ItemType Directory -Force (Split-Path $file) | Out-Null
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile "$file.part"
    if ((Hash "$file.part" $algo) -ne $hash) { Remove-Item "$file.part"; throw "checksum mismatch: $url" }
    Move-Item -Force "$file.part" $file
}

# compile-time classpath: Minecraft server + its libraries, Fabric Loader; Fabric API for the probe.
# Same pinned versions and hashes as launcher\...\Setup.java.
$deps = "$root\runtime\build-deps"
Fetch 'https://piston-data.mojang.com/v1/objects/33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c/server.jar' "$deps\server.jar" SHA1 '33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c'
Fetch 'https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar' "$deps\fabric-loader-0.19.5.jar" SHA256 '93044e4dd46de5d8136701292f05e868da096d2c9fddb4793e4fdbcc63efc695'
Fetch 'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.162.0+26.3/fabric-api-0.162.0+26.3.jar' "$deps\fabric-api-0.162.0+26.3.jar" SHA256 '8626c189b702f466800b1c8ddac6203cf8a1e0bdaee75a5f9a33b18c2baa81b1'
# unpack the real server jar and its libraries from Mojang's bundler jar (each checked against the bundler's SHA-256)
$zip = [IO.Compression.ZipFile]::OpenRead("$deps\server.jar")
try {
    foreach ($list in 'libraries', 'versions') {
        $r = New-Object IO.StreamReader($zip.GetEntry("META-INF/$list.list").Open())
        while ($null -ne ($line = $r.ReadLine())) {
            $f = $line -split "`t"
            if ($f.Count -lt 3) { continue }
            $target = "$deps\$list\$($f[2] -replace '/','\')"
            if ((Test-Path $target) -and (Hash $target SHA256) -eq $f[0]) { continue }
            New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry("META-INF/$list/$($f[2])"), $target, $true)
            if ((Hash $target SHA256) -ne $f[0]) { throw "checksum mismatch unpacking $($f[2])" }
        }
        $r.Close()
    }
} finally { $zip.Dispose() }
$mc = "$deps\versions\26.3\server-26.3.jar"
$libs = Get-ChildItem -Recurse "$deps\libraries" -Filter *.jar | ForEach-Object { $_.FullName }
$mcCp = (@($mc) + $libs + @("$deps\fabric-loader-0.19.5.jar")) -join ';'

# 1. backend core: shared sources only (src)
ArgFile "$build\core.args" $mcCp "$build\core"
Run "$Jdk\bin\javac.exe" (@("@$build\core.args") + (Get-ChildItem -Recurse "$root\src" -Filter *.java | ForEach-Object { $_.FullName }))
Run "$Jdk\bin\jar.exe" @('cf', "$build\app\lib\locator-core.jar", '-C', "$build\core", '.')

# 2. launcher (no Minecraft classes)
ArgFile "$build\launcher.args" '' "$build\launcher"
Run "$Jdk\bin\javac.exe" (@("@$build\launcher.args") + (Get-ChildItem -Recurse "$root\launcher" -Filter *.java | ForEach-Object { $_.FullName }))
Run "$Jdk\bin\jar.exe" @('cfe', "$build\app\locator.jar", 'dev.vplus.locator.launch.Launcher', '-C', "$build\launcher", '.')

# 3. probe mod for the verification server (needs the lifecycle API nested in Fabric API)
$fapi = "$deps\fabric-api-0.162.0+26.3.jar"
Push-Location "$build\nested"; Run "$Jdk\bin\jar.exe" @('xf', $fapi, 'META-INF/jars'); Pop-Location
$nested = Get-ChildItem -Recurse "$build\nested" -Filter *.jar | ForEach-Object { $_.FullName }
ArgFile "$build\probe.args" ((@($mcCp) + $nested) -join ';') "$build\probe"
Run "$Jdk\bin\javac.exe" (@("@$build\probe.args") + (Get-ChildItem -Recurse "$root\probe-mod\src" -Filter *.java | ForEach-Object { $_.FullName }))
Copy-Item "$root\probe-mod\fabric.mod.json" "$build\probe"
Run "$Jdk\bin\jar.exe" @('cf', "$build\app\lib\locator-probe.jar", '-C', "$build\probe", '.')

# 4. app folder: web UI, the released Prismatic Geodes jar, log config
Copy-Item "$root\web\index.html", "$root\web\app.js", "$root\web\style.css", "$root\web\icon.png" "$build\app\web"
Copy-Item $ModJar "$build\app\lib"
Copy-Item "$root\launcher\log4j2.xml" "$build\app"

# 5. bundled Java runtime (with bin\java.exe, which the verification server needs)
$modules = 'java.se,jdk.unsupported,jdk.zipfs,jdk.httpserver,jdk.management,jdk.crypto.ec,jdk.crypto.mscapi,jdk.crypto.cryptoki,jdk.naming.dns,jdk.charsets,jdk.localedata,jdk.net'
Run "$Jdk\bin\jlink.exe" @('--add-modules', $modules, '--strip-debug', '--no-header-files', '--no-man-pages', '--compress=zip-6', '--output', "$build\runtime")

# 6. native launcher (app image)
New-Item -ItemType Directory -Force $Out | Out-Null
Remove-Item -Recurse -Force "$Out\$name" -ErrorAction SilentlyContinue
# jpackage puts every jar it is given (even in subfolders) on the class path, so it only gets the launcher;
# lib\, web\ and the log config are copied into the image afterwards
New-Item -ItemType Directory -Force "$build\input" | Out-Null
Move-Item "$build\app\locator.jar" "$build\input"
Run "$Jdk\bin\jpackage.exe" @('--type', 'app-image', '--name', $name, '--app-version', $Version, '--vendor', 'Prismatic Geodes',
    '--description', 'Finds the coloured geodes of Prismatic Geodes from a world seed', '--icon', "$root\locator.ico",
    '--input', "$build\input", '--main-jar', 'locator.jar', '--main-class', 'dev.vplus.locator.launch.Launcher',
    '--runtime-image', "$build\runtime", '--java-options', '-Xmx2560m', '--dest', $Out)
Copy-Item -Recurse "$build\app\*" "$Out\$name\app"

# 7. user docs next to the exe (plain .txt so a double-click opens them)
if ($Docs) {
    Copy-Item "$Docs\docs\locator\README.txt" "$Out\$name\README.txt"
    Copy-Item "$Docs\docs\TROUBLESHOOTING.md" "$Out\$name\TROUBLESHOOTING.txt"
    foreach ($d in 'CREDITS', 'THIRD-PARTY-NOTICES') { Copy-Item "$Docs\$d.md" "$Out\$name\$d.txt" }
    Copy-Item "$Docs\LICENSE" "$Out\$name\LICENSE.txt"
}

# 8. privacy audit: no developer paths, private world or private extension may end up in the public build
# extra developer-specific patterns (names, paths, private worlds) can be listed one regex per line in the local,
# never published file audit-patterns.local.txt next to this script
$patterns = @('src-private', 'web-private')
if (Test-Path "$root\audit-patterns.local.txt") {
    $patterns += Get-Content "$root\audit-patterns.local.txt" | Where-Object { $_.Trim() -and -not $_.StartsWith('#') }
}
$bad = $patterns -join '|'
$hits = @()
Get-ChildItem -Recurse "$Out\$name" -File | Where-Object { $_.FullName -notlike "*\runtime\*" } | ForEach-Object {
    if ($_.Extension -eq '.jar') {
        $zip = [IO.Compression.ZipFile]::OpenRead($_.FullName)
        foreach ($e in $zip.Entries) {
            if ($e.Length -eq 0) { continue }
            $r = New-Object IO.StreamReader($e.Open()); $text = $r.ReadToEnd(); $r.Close()
            if ($text -cmatch $bad -or $e.FullName -cmatch $bad) { $hits += "$($_.Name)!$($e.FullName): $($Matches[0])" }
        }
        $zip.Dispose()
    } elseif ((Get-Content -Raw $_.FullName) -cmatch $bad) { $hits += "$($_.FullName): $($Matches[0])" }
}
if ($hits) { $hits | ForEach-Object { Write-Host "PRIVATE REFERENCE: $_" }; throw 'privacy audit failed' }
Write-Host 'privacy audit: clean'

# 9. ZIP
$zipName = "$($name -replace ' ', '-')-$Version-windows-x64.zip"
Remove-Item -Force "$Out\$zipName" -ErrorAction SilentlyContinue
[IO.Compression.ZipFile]::CreateFromDirectory("$Out\$name", "$Out\$zipName", [IO.Compression.CompressionLevel]::Optimal, $true)
Write-Host "app image: $Out\$name"
Write-Host "zip:       $Out\$zipName"

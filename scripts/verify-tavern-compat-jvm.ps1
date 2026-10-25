param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string]$PresetPath = 'E:\Localsend download\【小猫之神·喵赛亚】1.5.json',
    [string]$AllNeedsZip = 'E:\Localsend download\all-needs.zip'
)

$ErrorActionPreference = 'Stop'
$jarBase = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'

function Find-Jar([string]$relativeDir, [string]$filter) {
    $dir = Join-Path $jarBase $relativeDir
    if (-not (Test-Path $dir)) { throw "Gradle cache artifact missing: $relativeDir" }
    $jar = Get-ChildItem $dir -Recurse -Filter $filter -File |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $jar) { throw "Jar missing: $relativeDir\$filter" }
    return $jar.FullName
}

$compiler = Find-Jar 'org.jetbrains.kotlin\kotlin-compiler-embeddable\2.4.21' 'kotlin-compiler-embeddable-*.jar'
$stdlib = Find-Jar 'org.jetbrains.kotlin\kotlin-stdlib\2.4.21' 'kotlin-stdlib-*.jar'
$serializationJson = Find-Jar 'org.jetbrains.kotlinx\kotlinx-serialization-json-jvm\1.11.0' 'kotlinx-serialization-json-jvm-*.jar'
$serializationCore = Find-Jar 'org.jetbrains.kotlinx\kotlinx-serialization-core-jvm\1.10.0' 'kotlinx-serialization-core-jvm-*.jar'
$coroutines = Find-Jar 'org.jetbrains.kotlinx\kotlinx-coroutines-core-jvm' 'kotlinx-coroutines-core-jvm-*.jar'
$annotations = Find-Jar 'org.jetbrains\annotations\23.0.0' 'annotations-*.jar'
$okhttp = Find-Jar 'com.squareup.okhttp3\okhttp\4.12.0' 'okhttp-*.jar'
$okio = Find-Jar 'com.squareup.okio\okio-jvm\3.17.0' 'okio-jvm-*.jar'

$androidJar = $null
$localProperties = Join-Path $ProjectRoot 'local.properties'
if (Test-Path $localProperties) {
    $sdkLine = Get-Content $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($sdkLine) {
        $sdkDir = $sdkLine.Substring('sdk.dir='.Length).Replace('\\', '\')
        $platformsDir = Join-Path $sdkDir 'platforms'
        if (Test-Path $platformsDir) {
            $androidJar = Get-ChildItem $platformsDir -Directory |
                Sort-Object Name -Descending |
                ForEach-Object { Join-Path $_.FullName 'android.jar' } |
                Where-Object { Test-Path $_ } |
                Select-Object -First 1
        }
    }
}
if (-not $androidJar) { throw 'android.jar not found; set sdk.dir in local.properties' }

$classesDirs = Get-ChildItem $ProjectRoot -Directory | ForEach-Object {
    Join-Path $_.FullName 'build\intermediates\built_in_kotlinc\debug\compileDebugKotlin\classes'
} | Where-Object { Test-Path $_ }
if ($classesDirs.Count -eq 0) {
    throw 'No debug Kotlin classes found. Build once with Gradle (debug) before running this host-only verifier.'
}

$compileCp = (@($classesDirs) + $stdlib + $serializationJson + $serializationCore + $coroutines + $annotations + $androidJar) -join ';'
$runCp = (@($classesDirs) + $stdlib + $serializationJson + $serializationCore + $coroutines + $annotations + $okhttp + $okio + $androidJar) -join ';'
$tmp = Join-Path $env:TEMP ('rikka-tavern-compat-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

try {
    $compatSource = Join-Path $ProjectRoot 'app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernScriptCompat.kt'
    $scriptsSource = Join-Path $ProjectRoot 'app/src/main/java/me/rerere/rikkahub/data/st/runtime/TavernScripts.kt'
    $chainTest = Join-Path $PSScriptRoot 'tavern-compat\TavernScriptsChainSelfTest.kt'
    $javaHarness = Join-Path $PSScriptRoot 'tavern-compat\TavernCompatHarness.java'
    $chainOut = Join-Path $tmp 'chain-classes'
    $javaOut = Join-Path $tmp 'java-classes'
    New-Item -ItemType Directory -Force -Path $chainOut, $javaOut | Out-Null

    Write-Host '[1/4] Compiling TavernScriptCompat + TavernScripts + chain self-test (host kotlinc, no Gradle)...'
    $kotlinArgs = @(
        '-no-stdlib', '-no-reflect',
        '-classpath', $compileCp,
        '-d', $chainOut,
        $compatSource, $scriptsSource, $chainTest
    )
    & java -cp "$compiler;$stdlib;$coroutines;$annotations" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler @kotlinArgs
    if ($LASTEXITCODE -ne 0) { throw "Kotlin compile failed: $LASTEXITCODE" }

    Write-Host '[2/4] Running card-script chain self-test...'
    & java -cp "$chainOut;$runCp" me.rerere.rikkahub.data.st.runtime.TavernScriptsChainSelfTest
    if ($LASTEXITCODE -ne 0) { throw "chain self-test failed: $LASTEXITCODE" }

    Write-Host '[3/4] Compiling pure-JVM preset / all-needs harness...'
    & javac -encoding UTF-8 -cp $runCp -d $javaOut $javaHarness
    if ($LASTEXITCODE -ne 0) { throw "javac failed: $LASTEXITCODE" }

    Write-Host '[4/4] Running preset import + all-needs install harness...'
    # 中文路径经 PowerShell 5.1 -> Java 环境变量会变成 ANSI 乱码；先复制到 ASCII 临时路径。
    $presetTmp = Join-Path $tmp 'preset.json'
    $allNeedsTmp = Join-Path $tmp 'all-needs.zip'
    Copy-Item -LiteralPath $PresetPath -Destination $presetTmp -Force
    Copy-Item -LiteralPath $AllNeedsZip -Destination $allNeedsTmp -Force
    $env:CAT_GOD_PRESET = $presetTmp
    $env:ALL_NEEDS_ZIP = $allNeedsTmp
    & java -cp "$javaOut;$runCp" TavernCompatHarness
    if ($LASTEXITCODE -ne 0) { throw "JVM harness failed: $LASTEXITCODE" }
}
finally {
    Remove-Item Env:CAT_GOD_PRESET -ErrorAction SilentlyContinue
    Remove-Item Env:ALL_NEEDS_ZIP -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}

param(
    [string]$Repo = 'https://github.com/rikkahub/rikkahub.git',
    [string]$ExpectedBase = '447bb7e89710d31f1204d7a2973baa19fdbd5b28',
    [string]$ExpectedTarget = 'eeef60b22ba24383daf51f3c6c2e5b75bbab6bba'
)
$ErrorActionPreference = 'Stop'
$tmp = Join-Path $env:TEMP "rikkahub-upstream-check-$([guid]::NewGuid())"
git clone --filter=blob:none --no-checkout $Repo $tmp
try {
    $base = git -C $tmp rev-parse '2.5.6^{commit}'
    $target = git -C $tmp rev-parse '3.1.0^{commit}'
    if ($base -ne $ExpectedBase) { throw "2.5.6 mismatch: $base" }
    if ($target -ne $ExpectedTarget) { throw "3.1.0 mismatch: $target" }
    Write-Output "upstream commits OK: $base -> $target"
} finally {
    Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
}

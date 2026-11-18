param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CompanionArgs
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AppHome = Split-Path -Parent $ScriptDir

$JarFile = Get-ChildItem -Path (Join-Path $AppHome 'lib') -Filter 'drivepad-companion-*-all.jar' -File | Select-Object -First 1
if (-not $JarFile) {
    Write-Error "Could not locate drivepad-companion-*-all.jar under $(Join-Path $AppHome 'lib')."
    exit 1
}

$BundledJava = Join-Path $AppHome 'runtime\bin\java.exe'
$JavaExe = if (Test-Path $BundledJava) {
    $BundledJava
} elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmd) { $cmd.Source } else { $null }
}

if (-not $JavaExe) {
    Write-Error "Java 21+ (64-bit) runtime not found. Install Eclipse Temurin 21 x64 or add a jlink runtime under $AppHome\runtime."
    exit 1
}

& $JavaExe -jar $JarFile.FullName @CompanionArgs
exit $LASTEXITCODE

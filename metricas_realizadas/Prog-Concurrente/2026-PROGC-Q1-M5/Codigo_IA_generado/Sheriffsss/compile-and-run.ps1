param(
	[switch] $CompileOnly,
	[string] $Main = "SheriffsssPackage.Main"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = $PSScriptRoot
$SourceDir = Join-Path $ProjectRoot "src"
$ResourcesDir = Join-Path $ProjectRoot "resources"
$OutDir = Join-Path $ProjectRoot "out"

if (-not (Test-Path (Join-Path $SourceDir "SheriffsssPackage\Main.java"))) {
	throw "No se encontro src\SheriffsssPackage\Main.java."
}
if (-not (Test-Path $ResourcesDir)) {
	throw "No se encontro la carpeta resources."
}

$sources = @(Get-ChildItem -Path $SourceDir -Recurse -Filter *.java | ForEach-Object { $_.FullName })
if ($sources.Count -eq 0) {
	throw "No se encontraron fuentes Java en src."
}

if (Test-Path $OutDir) {
	Remove-Item -LiteralPath $OutDir -Recurse -Force
}
New-Item -ItemType Directory -Path $OutDir | Out-Null

& javac -encoding UTF-8 -d $OutDir $sources
if ($LASTEXITCODE -ne 0) {
	throw "La compilacion Java fallo."
}

if ($CompileOnly) {
	Write-Host "Compilacion OK: $OutDir"
	exit 0
}

$classpath = $OutDir + ";" + $ResourcesDir
$previousLocation = Get-Location
try {
	Set-Location $ProjectRoot
	& java -cp $classpath $Main
	if ($LASTEXITCODE -ne 0) {
		throw "La ejecucion del juego fallo."
	}
} finally {
	Set-Location $previousLocation
}

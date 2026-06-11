param(
	[string] $MainClass = "SheriffsssPackage.Main",
	[string] $AppName = "Sheriffsss",
	[int] $TrainingDefaultTargets = 5,
	[switch] $Console
)

$ErrorActionPreference = "Stop"

$ProjectRoot = $PSScriptRoot
$SourceDir = Join-Path $ProjectRoot "src"
$ResourcesDir = Join-Path $ProjectRoot "resources"
$BuildDir = Join-Path $ProjectRoot ".build\portable-exe"
$ClassesDir = Join-Path $BuildDir "classes"
$JarInputDir = Join-Path $BuildDir "input"
$JarPath = Join-Path $JarInputDir "$AppName.jar"
$PackageDir = Join-Path $ProjectRoot "dist-portable"
$AppDir = Join-Path $PackageDir $AppName
$AppRuntimeDir = Join-Path $AppDir "runtime"
$AppJarDir = Join-Path $AppDir "app"
$AppJarPath = Join-Path $AppJarDir "$AppName.jar"
$LauncherSource = Join-Path $BuildDir "$AppName`Launcher.cs"
$ExePath = Join-Path $AppDir "$AppName.exe"
$IconPng = Join-Path $ResourcesDir "sprites\sheriffsss_icono.png"
$IconIco = Join-Path $BuildDir "sheriffsss_icono.ico"

if (-not (Test-Path (Join-Path $SourceDir "SheriffsssPackage\Main.java"))) {
	throw "No se encontro src\SheriffsssPackage\Main.java."
}
if (-not (Test-Path $ResourcesDir)) {
	throw "No se encontro la carpeta resources."
}
if (-not (Test-Path $IconPng)) {
	throw "No se encontro $IconPng."
}

function Require-Tool {
	param(
		[string] $Name,
		[string] $Message
	)

	$command = Get-Command $Name -ErrorAction SilentlyContinue
	if ($command -eq $null) {
		$foundPath = Find-JdkTool -Name $Name
		if ($foundPath -eq $null) {
			throw $Message
		}
		return $foundPath
	}
	return $command.Source
}

function Find-JdkTool {
	param(
		[string] $Name
	)

	$candidates = New-Object System.Collections.Generic.List[string]
	$javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME")
	if (-not [string]::IsNullOrWhiteSpace($javaHome)) {
		$candidates.Add((Join-Path $javaHome "bin\$Name.exe"))
	}

	$roots = @(
		(Join-Path $env:ProgramFiles "Java"),
		(Join-Path $env:ProgramFiles "Eclipse Adoptium"),
		(Join-Path ${env:ProgramFiles(x86)} "Java")
	)
	foreach ($root in $roots) {
		if ([string]::IsNullOrWhiteSpace($root) -or -not (Test-Path $root)) {
			continue
		}
		$matches = Get-ChildItem -LiteralPath $root -Directory -Recurse -ErrorAction SilentlyContinue |
			Where-Object { $_.Name -like "jdk*" } |
			Sort-Object FullName -Descending
		foreach ($match in $matches) {
			$candidates.Add((Join-Path $match.FullName "bin\$Name.exe"))
		}
	}

	foreach ($candidate in $candidates) {
		if (Test-Path $candidate) {
			return $candidate
		}
	}
	return $null
}

function New-IcoFromPng {
	param(
		[string] $PngPath,
		[string] $IcoPath
	)

	Add-Type -AssemblyName System.Drawing

	$sizes = @(16, 24, 32, 48, 64, 128, 256)
	$pngFrames = New-Object System.Collections.Generic.List[byte[]]
	$source = [System.Drawing.Image]::FromFile($PngPath)
	try {
		foreach ($size in $sizes) {
			$bitmap = New-Object System.Drawing.Bitmap $size, $size
			$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
			try {
				$graphics.Clear([System.Drawing.Color]::Transparent)
				$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
				$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
				$graphics.DrawImage($source, 0, 0, $size, $size)

				$stream = New-Object System.IO.MemoryStream
				try {
					$bitmap.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
					$pngFrames.Add($stream.ToArray())
				} finally {
					$stream.Dispose()
				}
			} finally {
				$graphics.Dispose()
				$bitmap.Dispose()
			}
		}
	} finally {
		$source.Dispose()
	}

	$writer = New-Object System.IO.BinaryWriter([System.IO.File]::Create($IcoPath))
	try {
		$writer.Write([UInt16] 0)
		$writer.Write([UInt16] 1)
		$writer.Write([UInt16] $sizes.Count)

		$offset = 6 + (16 * $sizes.Count)
		for ($i = 0; $i -lt $sizes.Count; $i++) {
			$size = $sizes[$i]
			$bytes = $pngFrames[$i]
			$writer.Write([byte] $(if ($size -eq 256) { 0 } else { $size }))
			$writer.Write([byte] $(if ($size -eq 256) { 0 } else { $size }))
			$writer.Write([byte] 0)
			$writer.Write([byte] 0)
			$writer.Write([UInt16] 1)
			$writer.Write([UInt16] 32)
			$writer.Write([UInt32] $bytes.Length)
			$writer.Write([UInt32] $offset)
			$offset += $bytes.Length
		}

		for ($i = 0; $i -lt $sizes.Count; $i++) {
			$writer.Write($pngFrames[$i])
		}
	} finally {
		$writer.Dispose()
	}
}

function Get-CSharpCompiler {
	$candidates = @(
		"$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe",
		"$env:WINDIR\Microsoft.NET\Framework\v4.0.30319\csc.exe"
	)
	foreach ($candidate in $candidates) {
		if (Test-Path $candidate) {
			return $candidate
		}
	}
	throw "No se encontro csc.exe para generar el ejecutable launcher."
}

function Remove-DirectoryWithRetry {
	param(
		[string] $Path,
		[int] $Attempts = 8,
		[int] $DelayMilliseconds = 500
	)

	if (-not (Test-Path $Path)) {
		return
	}

	for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
		try {
			[System.GC]::Collect()
			[System.GC]::WaitForPendingFinalizers()
			Get-ChildItem -LiteralPath $Path -Recurse -Force -ErrorAction SilentlyContinue |
				ForEach-Object { $_.Attributes = "Normal" }
			Remove-Item -LiteralPath $Path -Recurse -Force
			return
		} catch {
			if ($attempt -eq $Attempts) {
				throw
			}
			Start-Sleep -Milliseconds $DelayMilliseconds
		}
	}
}

$javac = Require-Tool "javac" "No se encontro javac. Para generar el paquete portable hace falta un JDK en esta maquina de build."
$java = Require-Tool "java" "No se encontro java. Para verificar el jar hace falta un JDK/JRE en esta maquina de build."
$jlink = Require-Tool "jlink" "No se encontro jlink. Para generar el runtime portable hace falta un JDK completo."

Remove-DirectoryWithRetry -Path $BuildDir
Remove-DirectoryWithRetry -Path $PackageDir
New-Item -ItemType Directory -Path $ClassesDir | Out-Null
New-Item -ItemType Directory -Path $JarInputDir | Out-Null
New-Item -ItemType Directory -Path $AppJarDir | Out-Null

$sources = @(Get-ChildItem -Path $SourceDir -Recurse -Filter *.java | ForEach-Object { $_.FullName })
if ($sources.Count -eq 0) {
	throw "No se encontraron fuentes Java en src."
}

& $javac -encoding UTF-8 -d $ClassesDir $sources
if ($LASTEXITCODE -ne 0) {
	throw "La compilacion Java fallo."
}

Get-ChildItem -LiteralPath $ResourcesDir -Force | Copy-Item -Destination $ClassesDir -Recurse -Force
if (-not (Test-Path (Join-Path $ClassesDir "sprites\Pasto.png"))) {
	throw "No se copiaron correctamente los recursos dentro del jar."
}
New-Item -ItemType Directory -Path (Join-Path $ClassesDir "saves") | Out-Null
Set-Content -LiteralPath (Join-Path $ClassesDir "saves\training.cfg") -Value "count=$TrainingDefaultTargets" -Encoding ASCII

$ManifestDir = Join-Path $ClassesDir "META-INF"
New-Item -ItemType Directory -Path $ManifestDir -Force | Out-Null
$ManifestPath = Join-Path $ManifestDir "MANIFEST.MF"
$manifest = "Manifest-Version: 1.0`r`nMain-Class: $MainClass`r`n`r`n"
Set-Content -LiteralPath $ManifestPath -Value $manifest -Encoding ASCII -NoNewline

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$zipMode = [System.IO.Compression.ZipArchiveMode]::Create
$fileMode = [System.IO.FileMode]::Create
$fileStream = [System.IO.File]::Open($JarPath, $fileMode)
try {
	$archive = New-Object System.IO.Compression.ZipArchive($fileStream, $zipMode)
	try {
		$manifestEntry = $archive.CreateEntry("META-INF/MANIFEST.MF")
		$entryStream = $manifestEntry.Open()
		try {
			$bytes = [System.Text.Encoding]::ASCII.GetBytes($manifest)
			$entryStream.Write($bytes, 0, $bytes.Length)
		} finally {
			$entryStream.Dispose()
		}

		$files = Get-ChildItem -Path $ClassesDir -Recurse -File | Where-Object {
			$_.FullName -ne $ManifestPath
		}
		foreach ($file in $files) {
			$relativePath = $file.FullName.Substring($ClassesDir.Length + 1).Replace("\", "/")
			[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $relativePath) | Out-Null
		}
	} finally {
		$archive.Dispose()
	}
} finally {
	$fileStream.Dispose()
}

& $java -cp $JarPath $MainClass --packaging-check
if ($LASTEXITCODE -ne 0) {
	throw "El jar generado no pudo iniciar la clase principal."
}

New-IcoFromPng -PngPath $IconPng -IcoPath $IconIco

Copy-Item -LiteralPath $JarPath -Destination $AppJarPath

& $jlink `
	--add-modules "java.desktop,java.logging,java.prefs,java.datatransfer" `
	--bind-services `
	--strip-debug `
	--no-header-files `
	--no-man-pages `
	--output $AppRuntimeDir
if ($LASTEXITCODE -ne 0) {
	throw "jlink fallo al generar el runtime portable."
}

$primaryJavaExeName = if ($Console) { "java.exe" } else { "javaw.exe" }
$fallbackJavaExeName = if ($Console) { "javaw.exe" } else { "java.exe" }

@"
using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Windows.Forms;

internal static class SheriffsssLauncher
{
	[STAThread]
	private static int Main(string[] args)
	{
		string baseDir = AppDomain.CurrentDomain.BaseDirectory;
		string runtimeBinDir = Path.Combine(baseDir, "runtime", "bin");
		string javaExe = Path.Combine(runtimeBinDir, "$primaryJavaExeName");
		if (!File.Exists(javaExe))
		{
			javaExe = Path.Combine(runtimeBinDir, "$fallbackJavaExeName");
		}
		if (!File.Exists(javaExe))
		{
			MessageBox.Show("No se encontro javaw.exe ni java.exe dentro del runtime portable.", "$AppName", MessageBoxButtons.OK, MessageBoxIcon.Error);
			return 1;
		}

		string jarPath = Path.Combine(baseDir, "app", "$AppName.jar");
		if (!File.Exists(jarPath))
		{
			MessageBox.Show("No se encontro " + jarPath, "$AppName", MessageBoxButtons.OK, MessageBoxIcon.Error);
			return 1;
		}

		string extraArgs = string.Join(" ", args.Select(Quote));
		ProcessStartInfo startInfo = new ProcessStartInfo(javaExe);
		startInfo.WorkingDirectory = baseDir;
		startInfo.UseShellExecute = false;
		startInfo.Arguments = "-cp " + Quote(jarPath) + " $MainClass" + (extraArgs.Length == 0 ? "" : " " + extraArgs);
		Process.Start(startInfo);
		return 0;
	}

	private static string Quote(string value)
	{
		return "\"" + value.Replace("\"", "\\\"") + "\"";
	}
}
"@ | Set-Content -LiteralPath $LauncherSource -Encoding UTF8

$csc = Get-CSharpCompiler
$launcherTarget = if ($Console) { "exe" } else { "winexe" }
& $csc /nologo /target:$launcherTarget /win32icon:$IconIco /out:$ExePath /reference:System.Windows.Forms.dll $LauncherSource
if ($LASTEXITCODE -ne 0) {
	throw "La generacion del ejecutable launcher fallo."
}

if (-not (Test-Path (Join-Path $AppRuntimeDir "bin\javaw.exe")) -and -not (Test-Path (Join-Path $AppRuntimeDir "bin\java.exe"))) {
	throw "El runtime generado no contiene javaw.exe ni java.exe."
}

$javaLauncher = Join-Path $AppRuntimeDir "bin\java.exe"
if (Test-Path $javaLauncher) {
	& $javaLauncher -cp $AppJarPath $MainClass --packaging-check
	if ($LASTEXITCODE -ne 0) {
		throw "El runtime portable no pudo iniciar la clase principal."
	}
}

New-Item -ItemType Directory -Path (Join-Path $AppDir "saves") -Force | Out-Null
Set-Content -LiteralPath (Join-Path $AppDir "saves\training.cfg") -Value "count=$TrainingDefaultTargets" -Encoding ASCII

$DebugCmdPath = Join-Path $AppDir "$AppName-debug.cmd"
$debugCmd = @"
@echo off
setlocal
cd /d "%~dp0"
echo Ejecutando $AppName con consola de debug...
echo.
"runtime\bin\java.exe" -cp "app\$AppName.jar" $MainClass %*
set EXIT_CODE=%ERRORLEVEL%
echo.
echo Exit code: %EXIT_CODE%
pause
exit /b %EXIT_CODE%
"@
Set-Content -LiteralPath $DebugCmdPath -Value $debugCmd -Encoding ASCII

Remove-DirectoryWithRetry -Path $BuildDir
Write-Host "Paquete portable generado: $AppDir"
Write-Host "Ejecutable: $ExePath"
Write-Host "Debug cmd: $DebugCmdPath"
Write-Host "El paquete generado incluye runtime Java; la PC destino no necesita JDK ni JRE."

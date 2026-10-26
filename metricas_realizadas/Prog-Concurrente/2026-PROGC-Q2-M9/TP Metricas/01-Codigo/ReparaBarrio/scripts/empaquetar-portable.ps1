param(
    [string]$Jdk = "$env:USERPROFILE/.jdks/temurin-21.0.12.1",
    [string]$H2Jar = "$env:USERPROFILE/.m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar"
)
$ErrorActionPreference = 'Stop'
$raizPaquete = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$targetPaquete = Join-Path $raizPaquete 'target'
if ((Get-Item -LiteralPath $targetPaquete).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Target enlazado' }
foreach ($herramienta in @('java','javac','jar')) {
    if (-not (Test-Path -LiteralPath "$Jdk/bin/$herramienta.exe" -PathType Leaf)) { throw "Falta $herramienta en $Jdk" }
}
if (-not (Test-Path -LiteralPath $H2Jar -PathType Leaf)) { throw 'Falta H2 2.3.232 local' }
$trabajoPaquete = Join-Path $targetPaquete ('portable-build-' + [guid]::NewGuid())
$clasesPaquete = Join-Path $trabajoPaquete 'classes'
New-Item -ItemType Directory -Path $clasesPaquete -Force | Out-Null
$listaPaquete = Join-Path $trabajoPaquete 'fuentes.txt'
$fuentesPaquete = @(Get-ChildItem -LiteralPath (Join-Path $raizPaquete 'src/main/java') -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' })
[IO.File]::WriteAllLines($listaPaquete,$fuentesPaquete,(New-Object Text.UTF8Encoding($false)))
& "$Jdk/bin/javac.exe" --release 21 -encoding UTF-8 -cp $H2Jar -d $clasesPaquete "@$listaPaquete"
if ($LASTEXITCODE -ne 0) { throw 'Fallo de compilacion' }
Copy-Item -Path (Join-Path $raizPaquete 'src/main/resources/*') -Destination $clasesPaquete -Recurse
$distribucionPaquete = Join-Path $targetPaquete 'distribucion'
$salidaPaquete = Join-Path $distribucionPaquete 'ReparaBarrio-portable'
$zipPaquete = "$salidaPaquete.zip"
if ((Test-Path -LiteralPath $salidaPaquete) -or (Test-Path -LiteralPath $zipPaquete)) { throw 'Distribucion existente: conservarla; no se sobrescribe' }
if ((Test-Path -LiteralPath $distribucionPaquete) -and ((Get-Item -LiteralPath $distribucionPaquete).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Distribucion enlazada' }
New-Item -ItemType Directory -Path "$salidaPaquete/app","$salidaPaquete/lib" -Force | Out-Null
$manifiestoPaquete = Join-Path $trabajoPaquete 'MANIFEST.MF'
[IO.File]::WriteAllText($manifiestoPaquete,"Manifest-Version: 1.0`r`nMain-Class: ar.edu.unlam.reparabarrio.arranque.ReparaBarrioApp`r`nClass-Path: ../lib/h2-2.3.232.jar`r`n`r`n",[Text.Encoding]::ASCII)
& "$Jdk/bin/jar.exe" "-J-Djava.io.tmpdir=$trabajoPaquete" --create --file "$salidaPaquete/app/ReparaBarrio.jar" --manifest $manifiestoPaquete -C $clasesPaquete .
if ($LASTEXITCODE -ne 0) { throw 'Fallo al crear JAR' }
Copy-Item -LiteralPath $H2Jar -Destination "$salidaPaquete/lib/h2-2.3.232.jar"
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'portable/Iniciar-ReparaBarrio.cmd'),(Join-Path $PSScriptRoot 'portable/iniciar-portable.ps1'),(Join-Path $PSScriptRoot 'portable/LEEME-WINDOWS.txt') -Destination $salidaPaquete
Compress-Archive -LiteralPath $salidaPaquete -DestinationPath $zipPaquete
Write-Output "JAR: $salidaPaquete/app/ReparaBarrio.jar"
Write-Output "ZIP: $zipPaquete"

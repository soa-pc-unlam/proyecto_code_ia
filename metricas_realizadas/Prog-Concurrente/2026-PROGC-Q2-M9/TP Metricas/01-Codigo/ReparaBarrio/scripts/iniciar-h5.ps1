param(
    [string]$Jdk = "$env:USERPROFILE\.jdks\temurin-21.0.12.1",
    [string]$H2Jar = "$env:USERPROFILE\.m2\repository\com\h2database\h2\2.3.232\h2-2.3.232.jar",
    [switch]$Compilar,
    [switch]$SoloCompilar
)
$ErrorActionPreference = 'Stop'
$raizProyecto = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$javaH5 = Join-Path $Jdk 'bin/java.exe'
$javacH5 = Join-Path $Jdk 'bin/javac.exe'
foreach ($archivoH5 in @($javaH5, $javacH5, $H2Jar)) {
    if (-not (Test-Path -LiteralPath $archivoH5 -PathType Leaf)) { throw "Archivo requerido ausente: $archivoH5" }
}
$h2Real = (Resolve-Path -LiteralPath $H2Jar).Path
$targetH5 = Join-Path $raizProyecto 'target'
if ((Test-Path -LiteralPath $targetH5) -and ((Get-Item -LiteralPath $targetH5).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Target enlazado no admitido' }
$clasesH5 = Join-Path $targetH5 'classes'
if ($Compilar -or $SoloCompilar) {
    New-Item -ItemType Directory -Force -Path $clasesH5 | Out-Null
    $fuentesH5 = @(Get-ChildItem -LiteralPath (Join-Path $raizProyecto 'src/main/java') -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' })
    $listaH5 = Join-Path $targetH5 'h5-fuentes.txt'
    [IO.File]::WriteAllLines($listaH5, $fuentesH5, (New-Object Text.UTF8Encoding($false)))
    # PowerShell 5 trata warnings de stderr como NativeCommandError al capturarlos.
    # El código nativo, no la presencia de stderr, decide el resultado.
    $ErrorActionPreference = 'Continue'
    & $javacH5 --release 21 -encoding UTF-8 -cp $h2Real -d $clasesH5 "@$listaH5"
    $codigoCompilacionH5 = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($codigoCompilacionH5 -ne 0) { throw "Compilación fallida: $codigoCompilacionH5" }
    Copy-Item -Path (Join-Path $raizProyecto 'src/main/resources/*') -Destination $clasesH5 -Recurse -Force
}
if ($SoloCompilar) { return }
if (-not (Test-Path -LiteralPath (Join-Path $clasesH5 'ar/edu/unlam/reparabarrio/arranque/ReparaBarrioApp.class'))) { throw 'Compilar primero con -Compilar o desde IntelliJ/JDK 21.' }
$logsH5 = Join-Path $raizProyecto 'metricas/logs/h5'
New-Item -ItemType Directory -Force -Path $logsH5 | Out-Null
$logH5 = Join-Path $logsH5 ('arranque-' + [DateTime]::Now.ToString('yyyyMMdd-HHmmss-fff') + '.txt')
$classpathH5 = $clasesH5 + ';' + $h2Real
$lineaH5 = '& "' + $javaH5 + '" -cp "' + $classpathH5 + '" ar.edu.unlam.reparabarrio.arranque.ReparaBarrioApp --raiz "' + $raizProyecto + '" --academico-nuevo'
Write-Host $lineaH5
$lineaH5 | Out-File -LiteralPath $logH5 -Encoding utf8
& $javaH5 --version | Out-File -LiteralPath $logH5 -Append -Encoding utf8
Write-Host 'La GUI pide consentimiento antes de crear datos nuevos en target/h5-*. No ejecutar mvn clean mientras esté abierta.'
$ErrorActionPreference = 'Continue'
& $javaH5 -cp $classpathH5 ar.edu.unlam.reparabarrio.arranque.ReparaBarrioApp --raiz $raizProyecto --academico-nuevo 2>&1 | Tee-Object -FilePath ($logH5 + '.salida.txt')
$codigoH5 = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
"EXIT=$codigoH5" | Out-File -LiteralPath $logH5 -Append -Encoding utf8
if ($codigoH5 -ne 0) { throw "La aplicación terminó con código $codigoH5. Ver $logH5" }

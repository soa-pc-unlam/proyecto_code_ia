$ErrorActionPreference = 'Stop'
try {
    $javaPortable = Get-Command java.exe -ErrorAction SilentlyContinue
    if (-not $javaPortable) { throw 'Falta Java 21. Instale un JRE/JDK 21 y agregue java.exe al PATH.' }
    $ErrorActionPreference = 'Continue'
    $versionPortable = & $javaPortable.Source -XshowSettings:properties -version 2>&1 | Out-String
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0 -or $versionPortable -notmatch 'java.specification.version\s*=\s*21\s') { throw 'Se requiere Java 21 en PATH.' }
    $raizPortable = (Get-Item -LiteralPath $PSScriptRoot).FullName
    foreach ($archivoPortable in @('app/ReparaBarrio.jar','lib/h2-2.3.232.jar')) {
        if (-not (Test-Path -LiteralPath (Join-Path $raizPortable $archivoPortable) -PathType Leaf)) { throw "Paquete incompleto: $archivoPortable" }
    }
    & $javaPortable.Source -jar (Join-Path $raizPortable 'app/ReparaBarrio.jar') --raiz $raizPortable --academico-nuevo --portable
    if ($LASTEXITCODE -ne 0) { throw "La aplicacion termino con codigo $LASTEXITCODE" }
} catch {
    Write-Host "No se pudo iniciar: $_"
    exit 1
}

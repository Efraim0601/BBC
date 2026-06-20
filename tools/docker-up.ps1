# Lance la stack BBC SMS via Docker.
# Corrige le PATH pour que Docker trouve l'assistant d'identifiants
# (docker-credential-desktop), absent du PATH de certains terminaux.
#
# Usage :  .\tools\docker-up.ps1            (build + up)
#          .\tools\docker-up.ps1 -Down      (arrêt + suppression)

param([switch]$Down)

$dockerBin = "C:\Program Files\Docker\Docker\resources\bin"
if (Test-Path $dockerBin) { $env:PATH = "$dockerBin;$env:PATH" }

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ($Down) {
    docker compose down
} else {
    docker compose up --build
}

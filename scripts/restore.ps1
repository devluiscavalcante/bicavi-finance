# Restaura um backup gerado por backup.ps1 no banco de producao do Bicavi.
#
# ATENCAO: SUBSTITUI todos os dados atuais pelos do backup.
#
# Uso (PowerShell, na raiz do projeto):
#   .\scripts\restore.ps1 -Arquivo "C:\...\Bicavi-backups\bicavi-2026-10-07_2200.dump"

param(
    [Parameter(Mandatory = $true)]
    [string]$Arquivo,
    # Pula a confirmacao (uso em testes/automacao). Cuidado.
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

if (-not (Test-Path $Arquivo)) { throw "Arquivo nao encontrado: $Arquivo" }

$config = @{}
Get-Content .env | Where-Object { $_ -match '^\s*([^#][^=]*)=(.*)$' } | ForEach-Object {
    $config[$matches[1].Trim()] = $matches[2].Trim()
}

if (-not $Force) {
    $resposta = Read-Host "Isto SUBSTITUI todos os dados atuais do Bicavi. Digite RESTAURAR para continuar"
    if ($resposta -ne 'RESTAURAR') {
        Write-Host "Cancelado."
        exit 1
    }
}

# O backend fica parado durante a restauracao: ninguem grava no meio do processo.
docker compose stop backend
try {
    docker compose cp $Arquivo db:/tmp/restore.dump
    if ($LASTEXITCODE -ne 0) { throw "Falha ao copiar o backup para o container" }

    # --clean --if-exists: apaga as tabelas atuais e recria a partir do backup.
    docker compose exec -T db pg_restore -U $config.DB_USER -d $config.DB_NAME --clean --if-exists --no-owner /tmp/restore.dump
    if ($LASTEXITCODE -ne 0) { throw "pg_restore falhou" }

    docker compose exec -T db rm -f /tmp/restore.dump | Out-Null
    Write-Host "Backup restaurado: $Arquivo"
}
finally {
    docker compose start backend
}

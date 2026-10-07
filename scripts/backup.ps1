# Backup do banco de producao do Bicavi (compose.yaml na raiz do projeto).
#
# Uso (PowerShell, na raiz do projeto):
#   .\scripts\backup.ps1
#   .\scripts\backup.ps1 -Destino "C:\Users\voce\OneDrive\Bicavi-backups" -ManterDias 60
#
# Dica: aponte -Destino para uma pasta do OneDrive/Google Drive. Assim o backup
# sai deste computador sozinho (se o disco morrer, o backup sobrevive).
#
# (Sem acentos de proposito: o PowerShell 5.1 le scripts sem BOM com a
# codificacao errada e os acentos sairiam corrompidos.)

param(
    [string]$Destino = (Join-Path $env:USERPROFILE "Documents\Bicavi-backups"),
    [int]$ManterDias = 30
)

$ErrorActionPreference = 'Stop'

# Roda a partir da raiz do projeto (onde estao compose.yaml e .env),
# nao importa de onde o script foi chamado.
Set-Location (Split-Path $PSScriptRoot -Parent)

# Le DB_NAME e DB_USER do .env (as mesmas variaveis que o compose usa).
$config = @{}
Get-Content .env | Where-Object { $_ -match '^\s*([^#][^=]*)=(.*)$' } | ForEach-Object {
    $config[$matches[1].Trim()] = $matches[2].Trim()
}

New-Item -ItemType Directory -Force -Path $Destino | Out-Null
$arquivo = Join-Path $Destino ("bicavi-{0:yyyy-MM-dd_HHmm}.dump" -f (Get-Date))

# O dump e gerado DENTRO do container e depois copiado. Redirecionar a saida do
# pg_dump direto para um arquivo no PowerShell 5.1 corromperia o conteudo binario.
# --format=custom: compactado e restauravel com pg_restore (ver restore.ps1).
docker compose exec -T db pg_dump -U $config.DB_USER -d $config.DB_NAME --format=custom -f /tmp/bicavi.dump
if ($LASTEXITCODE -ne 0) { throw "pg_dump falhou (o container 'db' esta rodando?)" }

docker compose cp db:/tmp/bicavi.dump $arquivo
if ($LASTEXITCODE -ne 0) { throw "Falha ao copiar o backup do container" }
docker compose exec -T db rm -f /tmp/bicavi.dump | Out-Null

$tamanho = [math]::Round((Get-Item $arquivo).Length / 1KB, 1)
Write-Host "Backup salvo: $arquivo ($tamanho KB)"

# Remove backups antigos desta pasta.
$limite = (Get-Date).AddDays(-$ManterDias)
Get-ChildItem $Destino -Filter 'bicavi-*.dump' |
    Where-Object { $_.LastWriteTime -lt $limite } |
    ForEach-Object {
        Remove-Item $_.FullName
        Write-Host "Removido backup antigo: $($_.Name)"
    }

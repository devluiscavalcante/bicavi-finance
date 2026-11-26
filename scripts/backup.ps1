# Backup do banco de producao do Bicavi (compose.yaml na raiz do projeto).
#
# Uso (PowerShell, na raiz do projeto):
#   .\scripts\backup.ps1
#   .\scripts\backup.ps1 -Destino "C:\Users\voce\OneDrive\Bicavi-backups" -ManterDias 60
#
# Dica: aponte -Destino para uma pasta do OneDrive/Google Drive. Assim o backup
# sai deste computador sozinho (se o disco morrer, o backup sobrevive).
#
# Cada backup novo e restaurado num banco descartavel (test-restore.ps1) antes
# de ser dado como bom. Cada execucao deixa uma linha (OK ou FALHOU) em
# backup.log, na mesma pasta dos backups.
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

New-Item -ItemType Directory -Force -Path $Destino | Out-Null

# Agendado, o backup roda sem janela: sem este registro, uma falha nao seria
# vista por ninguem. Fica junto dos backups (no OneDrive, abre pelo celular).
$log = Join-Path $Destino 'backup.log'
function Write-BackupLog([string]$mensagem) {
    Add-Content -Path $log -Encoding UTF8 -Value ("{0:yyyy-MM-dd HH:mm}  {1}" -f (Get-Date), $mensagem)
}

try {
    # Le DB_NAME e DB_USER do .env (as mesmas variaveis que o compose usa).
    $config = @{}
    Get-Content .env | Where-Object { $_ -match '^\s*([^#][^=]*)=(.*)$' } | ForEach-Object {
        $config[$matches[1].Trim()] = $matches[2].Trim()
    }

    $nome = "bicavi-{0:yyyy-MM-dd_HHmm}.dump" -f (Get-Date)
    $arquivo = Join-Path $Destino $nome

    # O dump e gerado DENTRO do container e depois copiado. Redirecionar a saida do
    # pg_dump direto para um arquivo no PowerShell 5.1 corromperia o conteudo binario.
    # --format=custom: compactado e restauravel com pg_restore (ver restore.ps1).
    docker compose exec -T db pg_dump -U $config.DB_USER -d $config.DB_NAME --format=custom -f /tmp/bicavi.dump
    if ($LASTEXITCODE -ne 0) { throw "pg_dump falhou (o container 'db' esta rodando?)" }

    docker compose cp db:/tmp/bicavi.dump $arquivo
    if ($LASTEXITCODE -ne 0) { throw "Falha ao copiar o backup do container" }

    try {
        $resumo = & (Join-Path $PSScriptRoot 'test-restore.ps1') -Arquivo $arquivo
    }
    catch {
        $erro = $_
        # O arquivo fica (para investigar), mas o nome avisa: quem procurar um
        # backup na pasta numa emergencia nao pega este por engano.
        # -Force: substitui um "-FALHOU" do mesmo minuto (duas execucoes seguidas).
        # SilentlyContinue: se nem renomear der certo, o erro que importa e o do teste.
        Move-Item $arquivo ($arquivo -replace '\.dump$', '-FALHOU.dump') -Force -ErrorAction SilentlyContinue
        throw $erro
    }

    $tamanho = [math]::Round((Get-Item $arquivo).Length / 1KB, 1)
    Write-Host "Backup salvo e testado: $arquivo ($tamanho KB; $resumo)"
    Write-BackupLog "OK      $nome ($tamanho KB) - restaurado no teste: $resumo"

    # Remove backups antigos desta pasta. So chega aqui com o backup de hoje
    # testado: se ele falhar, nenhum backup antigo e apagado.
    $limite = (Get-Date).AddDays(-$ManterDias)
    Get-ChildItem $Destino -Filter 'bicavi-*.dump' |
        Where-Object { $_.LastWriteTime -lt $limite } |
        ForEach-Object {
            Remove-Item $_.FullName
            Write-Host "Removido backup antigo: $($_.Name)"
        }
}
catch {
    # Uma linha por execucao: mensagens com quebra de linha viram uma linha so.
    Write-BackupLog ("FALHOU  " + ($_.Exception.Message -replace '\s*[\r\n]+\s*', ' ').Trim())
    # Repassa o erro: aparece na tela (quem roda a mao ve) e o script termina com
    # codigo 1, que o Agendador de Tarefas mostra em "Resultado da Ultima Execucao".
    throw
}
finally {
    # Apaga o dump de dentro do container, tenha dado certo ou nao.
    docker compose exec -T db rm -f /tmp/bicavi.dump | Out-Null
}

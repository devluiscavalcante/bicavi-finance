# Testa se um backup gerado por backup.ps1 pode mesmo ser restaurado.
#
# Um backup so vale se voltar: um arquivo cortado pela metade, ou o dump de um
# banco errado, parece um backup normal ate o dia em que alguem precisa dele.
#
# O teste restaura o arquivo num Postgres DESCARTAVEL, um container que so
# existe durante o teste: sem rede, sem porta aberta, apagado no fim. O banco
# de producao nao e tocado. Se der certo, devolve quantos usuarios e
# transacoes voltaram; se der errado, para com o erro.
#
# Uso (PowerShell, de qualquer pasta):
#   .\scripts\test-restore.ps1 -Arquivo "C:\...\Bicavi-backups\bicavi-2026-10-07_2200.dump"
#
# O backup.ps1 roda este teste sozinho a cada backup novo.
#
# (Sem acentos de proposito: ver backup.ps1.)

param(
    [Parameter(Mandatory = $true)]
    [string]$Arquivo
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $Arquivo)) { throw "Arquivo nao encontrado: $Arquivo" }

# Roda DENTRO do container de teste (bash). Vai para la como ARQUIVO, igual ao
# dump: como argumento, o PowerShell 5.1 estraga as aspas; pelo pipe (entrada
# padrao), ele pode acrescentar bytes invisiveis (BOM no inicio, CRLF no fim),
# que o bash nao ignora.
$teste = @'
# Tudo, inclusive as mensagens de erro, sai pela saida padrao: e de la que o
# PowerShell tira o resultado (ou o motivo da falha).
exec 2>&1
set -e

# Liga o Postgres vazio do proprio container.
docker-entrypoint.sh postgres > /tmp/postgres.log 2>&1 &

# Espera o servidor DEFINITIVO. Na primeira subida, a imagem liga um servidor
# provisorio (so para se configurar) e depois o reinicia. O provisorio nao
# atende pela rede: perguntando por 127.0.0.1, so o definitivo responde.
for i in $(seq 1 60); do
    pg_isready -q -h 127.0.0.1 -U postgres && break
    sleep 1
done
pg_isready -q -h 127.0.0.1 -U postgres || { echo "o Postgres de teste nao subiu"; exit 1; }

createdb -h 127.0.0.1 -U postgres teste

# --exit-on-error: qualquer erro reprova o teste.
# --no-owner --no-acl: o usuario do app (dono das tabelas na producao) nao
# existe aqui; as tabelas ficam com o administrador do Postgres de teste.
pg_restore -h 127.0.0.1 -U postgres -d teste --exit-on-error --no-owner --no-acl /tmp/teste.dump

# Prova de que os dados voltaram, e de que o backup e mesmo do Bicavi: num
# dump de outro banco, essas tabelas nao existem e a consulta falha.
psql -h 127.0.0.1 -U postgres -d teste -At -v ON_ERROR_STOP=1 -c "SELECT (SELECT count(*) FROM users) || ' usuarios, ' || (SELECT count(*) FROM transactions) || ' transacoes'"
'@

$id = $null
$scriptLocal = Join-Path $env:TEMP ("bicavi-test-restore-{0}.sh" -f [guid]::NewGuid())
try {
    # UTF-8 sem BOM e so LF no fim das linhas: exatamente o que o bash espera.
    [IO.File]::WriteAllText($scriptLocal, ($teste -replace "`r", ""), (New-Object System.Text.UTF8Encoding $false))

    # A mesma imagem do banco de producao (compose.yaml): mesma versao do Postgres.
    # --network none: o container de teste nao tem rede nenhuma.
    # trust: entra sem senha, ja que ninguem de fora alcanca este Postgres.
    $id = docker create --network none -e POSTGRES_HOST_AUTH_METHOD=trust postgres:17 bash /tmp/teste.sh
    if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel criar o container de teste (o Docker esta rodando?)" }

    docker cp $scriptLocal "${id}:/tmp/teste.sh" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Falha ao copiar o teste para o container" }
    docker cp $Arquivo "${id}:/tmp/teste.dump" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Falha ao copiar o backup para o container de teste" }

    # -a: espera o teste terminar e devolve a saida e o codigo de saida dele.
    $saida = @(docker start -a $id)
    if ($LASTEXITCODE -ne 0) {
        # O motivo: a ultima linha com "error" (assim escrevem o pg_restore e o
        # psql; depois dela o psql ainda mostra o trecho do comando e um "^").
        # Sem nenhuma, a ultima linha que nao esta em branco.
        $erros = @($saida | Where-Object { $_ -match 'error' })
        $motivo = if ($erros) { $erros[-1] } else { @($saida | Where-Object { "$_".Trim() })[-1] }
        throw "Teste de restauracao falhou: $("$motivo".Trim())"
    }
    # A ultima linha e o resumo do psql ("2 usuarios, 318 transacoes").
    $saida[-1]
}
finally {
    # Apaga o container (e, junto com ele, a copia restaurada dos dados) e o arquivo do teste.
    if ($id) { docker rm -f $id | Out-Null }
    Remove-Item $scriptLocal -Force -ErrorAction SilentlyContinue
}

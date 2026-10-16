# Cria (ou atualiza) o usuario do banco com que o BACKEND se conecta.
#
# Por que: o DB_USER do .env e o administrador do Postgres (superusuario).
# Se o backend entrasse com ele e fosse invadido (SQL injection, dependencia
# comprometida...), o invasor poderia rodar comandos no container
# (COPY ... TO PROGRAM), ler arquivos e criar outros usuarios. O usuario do app
# (APP_DB_USER) e comum: e so DONO do banco do Bicavi e das tabelas dele.
# Assim o Flyway continua criando e alterando tabelas, e o estrago de uma
# invasao fica nos dados do app.
#
# Uso (PowerShell, na raiz do projeto, com o container "db" rodando):
#   docker compose up -d db
#   .\scripts\create-app-role.ps1
#   docker compose up -d --build
#
# Pode rodar de novo sem problema: o que ja existe e so ajustado (por exemplo,
# depois de trocar APP_DB_PASSWORD no .env).
#
# (Sem acentos de proposito: ver backup.ps1.)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)

$config = @{}
Get-Content .env | Where-Object { $_ -match '^\s*([^#][^=]*)=(.*)$' } | ForEach-Object {
    $config[$matches[1].Trim()] = $matches[2].Trim()
}

$admin = $config.DB_USER
$appUser = $config.APP_DB_USER
$appPassword = $config.APP_DB_PASSWORD

# Os valores vao dentro de um comando SQL: so aceitamos caracteres que nao
# precisam de escape (e a senha gerada pelo comando do .env.example ja e assim).
if ($appUser -notmatch '^[a-z_][a-z0-9_]*$') {
    throw "APP_DB_USER invalido no .env (use letras minusculas, numeros e _)"
}
if ($appUser -eq $admin) {
    throw "APP_DB_USER precisa ser diferente de DB_USER (o administrador)"
}
if ($appPassword -notmatch '^[A-Za-z0-9]{20,}$') {
    throw "APP_DB_PASSWORD invalida no .env: use pelo menos 20 letras e numeros (gere com o comando do .env.example)"
}

# \gexec: o psql executa cada linha devolvida pelo SELECT como um comando.
# format() com %I (nome) e %L (texto) monta os comandos com as aspas certas.
$sql = @"
\set app_user '$appUser'
\set app_password '$appPassword'

-- Cria o usuario, se ainda nao existir.
SELECT format('CREATE ROLE %I LOGIN', :'app_user')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'app_user') \gexec

-- Garante que ele e um usuario COMUM e (re)define a senha.
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L',
              :'app_user', :'app_password') \gexec

-- Dono do banco: no Postgres 15+, o dono do banco e tambem dono do schema
-- public, entao pode criar as tabelas das proximas migrations.
SELECT format('ALTER DATABASE %I OWNER TO %I', current_database(), :'app_user') \gexec

-- Dono das tabelas que ja existem (inclusive a flyway_schema_history).
-- As sequencias dos ids (IDENTITY) e os indices acompanham a tabela.
SELECT format('ALTER TABLE %I.%I OWNER TO %I', schemaname, tablename, :'app_user')
FROM pg_tables WHERE schemaname = 'public' \gexec
"@

# O psql nao entende o fim de linha do Windows (CRLF) nos comandos "\".
$sql -replace "`r", "" | docker compose exec -T db psql -v ON_ERROR_STOP=1 -q -U $admin -d $config.DB_NAME
if ($LASTEXITCODE -ne 0) { throw "Falha ao configurar o usuario (o container 'db' esta rodando?)" }

Write-Host "Usuario '$appUser' pronto: comum (sem superusuario) e dono do banco '$($config.DB_NAME)'."

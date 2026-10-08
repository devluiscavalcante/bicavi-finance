# Instalação do Bicavi no PC de casa (Windows)

O Bicavi roda em containers Docker num computador de casa. O **Tailscale** cria uma
rede privada entre esse computador e os iPhones, com endereço **HTTPS válido**
(necessário para instalar o app no iPhone). Nada fica exposto na internet: só os
aparelhos da sua rede Tailscale acessam.

```
iPhone (app Tailscale) --HTTPS--> PC de casa: Tailscale -> nginx -> backend -> Postgres
```

O PC de casa **não precisa** de Java, Node ou Maven: o Docker compila tudo.

---

## 1. Instalar os programas (uma vez)

1. **Git**: https://git-scm.com/download/win (opções padrão).
2. **Docker Desktop**: https://www.docker.com/products/docker-desktop
   - Na instalação, mantenha **"Use WSL 2"** marcado. Reinicie se pedir.
   - Em *Settings → General*, marque **"Start Docker Desktop when you sign in"**.
3. **Energia do Windows**: em *Configurações → Sistema → Energia*, coloque
   **"Suspender: Nunca"** (com o PC dormindo, o app fica fora do ar).

## 2. Baixar o projeto

No PowerShell:

```powershell
git clone https://github.com/devluiscavalcante/bicavi-finance.git C:\bicavi
cd C:\bicavi
```

## 3. Criar o arquivo de senhas (`.env`)

```powershell
copy .env.example .env
notepad .env
```

Troque `DB_PASSWORD` e `JWT_SECRET` por valores **novos** (não reutilize os de
desenvolvimento). Para gerar cada um, rode no PowerShell e cole o resultado:

```powershell
-join ((48..57)+(65..90)+(97..122) | Get-Random -Count 40 | % {[char]$_})
```

> O `.env` nunca vai para o git. Guarde uma cópia num gerenciador de senhas:
> sem o `DB_PASSWORD`, um backup não pode ser restaurado em outra instalação.

## 4. Subir o app

```powershell
docker compose up -d --build
```

A primeira vez leva alguns minutos (baixa e compila tudo). Depois confira:

```powershell
docker compose ps
```

Os três serviços (`db`, `backend`, `web`) devem estar **Up** (o `db` como *healthy*).
Abra **http://localhost:8088** no navegador do PC: a tela de login deve aparecer.

## 5. Tailscale: HTTPS para os iPhones

1. Instale no PC: https://tailscale.com/download/windows e faça login
   (conta Google, Microsoft ou Apple).
2. No painel https://login.tailscale.com/admin/dns:
   - ative **MagicDNS**;
   - em **HTTPS Certificates**, clique em **Enable HTTPS**.
3. No PowerShell **como Administrador**:

   ```powershell
   tailscale serve --bg 8088
   ```

   O comando mostra o endereço do app, algo como
   `https://nome-do-pc.tailabc123.ts.net`. Essa configuração continua valendo
   depois de reiniciar o PC.

## 6. Instalar nos iPhones

Em **cada** iPhone:

1. Instale o app **Tailscale** (App Store) e entre na **mesma conta** do PC
   (ou, no painel do Tailscale, convide a outra pessoa para a sua rede).
   Deixe a VPN do Tailscale **ligada**.
2. Abra o endereço `https://...ts.net` no **Safari**.
3. Toque em **Compartilhar → Adicionar à Tela de Início**.
4. Abra o Bicavi pelo ícone. Na primeira vez, **crie a conta** (vocês usam o
   mesmo login). No outro iPhone, só entre com esse login.

> O app instalado tem armazenamento próprio: mesmo já tendo entrado pelo Safari,
> é preciso fazer login de novo dentro do app.

## 7. Backup automático (diário)

O script `scripts\backup.ps1` salva o banco num arquivo com data e hora e apaga
os backups com mais de 30 dias. Aponte o destino para uma pasta do **OneDrive** ou
**Google Drive**: assim o backup sai do computador sozinho.

Teste uma vez (troque o caminho pela sua pasta):

```powershell
powershell -ExecutionPolicy Bypass -File C:\bicavi\scripts\backup.ps1 -Destino "$env:USERPROFILE\OneDrive\Bicavi-backups"
```

Agende para todo dia às 22h (PowerShell **como Administrador**):

```powershell
$acao = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -File C:\bicavi\scripts\backup.ps1 -Destino `"$env:USERPROFILE\OneDrive\Bicavi-backups`""
$quando = New-ScheduledTaskTrigger -Daily -At 22:00
Register-ScheduledTask -TaskName 'Bicavi Backup' -Action $acao -Trigger $quando
```

## 8. Atualizar para uma versão nova

O PC de casa usa **sempre a branch `master`**, que só recebe código já validado.
O desenvolvimento acontece na branch `develop`; depois de testada, ela é mesclada
na `master` e enviada para o GitHub. Só então atualize aqui:

```powershell
cd C:\bicavi
git log -1 --oneline   # anote o commit atual: é para ele que você volta se der problema
powershell -ExecutionPolicy Bypass -File .\scripts\backup.ps1 -Destino "$env:USERPROFILE\OneDrive\Bicavi-backups"
git pull
docker compose up -d --build
```

**Sempre faça o backup antes do `git pull`.** Versões novas podem trazer
*migrations* (arquivos `V*.sql` em `backend/src/main/resources/db/migration`)
que alteram a estrutura do banco. Voltar o código não desfaz uma migration:
o que desfaz é restaurar o backup feito **antes** dela rodar.

Os iPhones recebem a versão nova sozinhos na próxima vez que o app abrir.

### Se a versão nova quebrou algo

1. Volte o código para o commit que você anotou:

   ```powershell
   cd C:\bicavi
   git checkout <commit-anotado>
   docker compose up -d --build
   ```

2. Se o backend não subir (`docker compose logs backend` mostra erro do
   **Flyway**, como *"applied migration not resolved locally"*), é porque a
   versão nova já aplicou uma migration que o código antigo não conhece.
   Restaure o backup feito antes do `pull` (seção 9) e suba de novo.
   Dados lançados **depois** desse backup se perdem: lance-os de novo.

3. Quando a correção chegar na `master`, volte para ela e atualize normalmente:

   ```powershell
   git switch master
   git pull
   docker compose up -d --build
   ```

## 9. Restaurar um backup

**Substitui todos os dados atuais** pelos do arquivo escolhido:

```powershell
cd C:\bicavi
powershell -ExecutionPolicy Bypass -File .\scripts\restore.ps1 -Arquivo "C:\...\Bicavi-backups\bicavi-2026-10-07_2200.dump"
```

O script pede para digitar `RESTAURAR` antes de continuar.

## Problemas comuns

| Sintoma | Causa provável |
|---|---|
| iPhone não abre o endereço `.ts.net` | VPN do Tailscale desligada no iPhone, ou PC de casa desligado/suspenso |
| `docker compose` dá erro de conexão | Docker Desktop não está aberto |
| Login pede senha de novo no app instalado | Normal no iPhone: o app tem armazenamento próprio |
| Página abre mas mostra erro ao carregar dados | Backend iniciando (aguarde ~20 s) ou parado: veja `docker compose logs backend` |

Comandos úteis:

```powershell
docker compose logs -f backend   # acompanhar o backend
docker compose restart backend   # reiniciar só o backend
docker compose down              # parar tudo (os dados ficam no volume)
```

> **Nunca** rode `docker compose down -v`: o `-v` apaga o volume do banco,
> ou seja, todos os dados.

# Instalação do Bicavi no PC de casa (Windows)

O Bicavi roda em containers Docker num computador de casa. O **Tailscale** cria uma
rede privada entre esse computador e os iPhones, com endereço **HTTPS válido**
(necessário para instalar o app no iPhone). Nada fica exposto na internet: só os
aparelhos da sua rede Tailscale acessam. Isso funciona **de qualquer lugar**
(Wi-Fi de casa, 4G, outra cidade), desde que o PC de casa esteja ligado e com internet.

```
iPhone (app Tailscale) --HTTPS--> PC de casa: Tailscale -> nginx -> backend -> Postgres
```

> **Nunca** use `tailscale funnel`: ele publica o app na internet aberta,
> e qualquer pessoa conseguiria criar conta.

O PC de casa **não precisa** de Java, Node ou Maven: o Docker compila tudo.

---

## 1. Instalar os programas (uma vez)

1. **Git**: https://git-scm.com/download/win (opções padrão).
2. **Docker Desktop**: https://www.docker.com/products/docker-desktop
   - Na instalação, mantenha **"Use WSL 2"** marcado. Reinicie se pedir.
   - Em *Settings → General*, marque **"Start Docker Desktop when you sign in"**.
3. **Energia do Windows**: em *Configurações → Sistema → Energia*, coloque
   **"Suspender: Nunca"** (com o PC dormindo, o app fica fora do ar).
4. **Voltar sozinho depois de reiniciar.** O Docker Desktop só abre quando alguém
   **entra no Windows**. Se o Windows Update reiniciar o PC de madrugada, o app
   fica fora do ar até alguém fazer login. Escolha uma das opções:
   - **Login automático** (o app volta sozinho): em *Configurações → Contas →
     Opções de entrada*, desligue *"Para maior segurança, permitir apenas a
     entrada do Windows Hello..."*. Depois rode `netplwiz`, desmarque *"Os
     usuários devem digitar um nome de usuário e uma senha..."* e confirme a
     senha. Contrapartida: quem tiver acesso físico ao PC entra sem senha.
   - **Sem login automático:** em *Configurações → Windows Update → Opções
     avançadas → Horário ativo*, defina o período em que vocês usam o app, e
     lembre de entrar no Windows sempre que o PC reiniciar.

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
4. No ícone do Tailscale (perto do relógio), abra as preferências e ative
   **"Run unattended"**: assim o PC continua na rede Tailscale mesmo antes de
   alguém entrar no Windows ou depois de sair da conta.

## 6. Instalar nos iPhones

Em **cada** iPhone:

1. Instale o app **Tailscale** (App Store) e entre na **mesma conta** do PC
   (ou, no painel do Tailscale, convide a outra pessoa para a sua rede).
   Deixe a VPN do Tailscale **ligada**.
2. No app Tailscale, nas configurações, ative **VPN On Demand**. O iOS às vezes
   desliga a VPN (ao reiniciar o celular, por exemplo); com isso ela religa sozinha.
3. Abra o endereço `https://...ts.net` no **Safari**.
4. Toque em **Compartilhar → Adicionar à Tela de Início**.
5. Abra o Bicavi pelo ícone. Na primeira vez, **crie a conta** (vocês usam o
   mesmo login). No outro iPhone, só entre com esse login.
6. **Teste fora de casa:** desligue o Wi-Fi do iPhone e abra o Bicavi pelo 4G.
   Se carregar, vai funcionar de qualquer lugar.

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
$opcoes = New-ScheduledTaskSettingsSet -StartWhenAvailable
Register-ScheduledTask -TaskName 'Bicavi Backup' -Action $acao -Trigger $quando -Settings $opcoes
```

`-StartWhenAvailable`: se o PC estiver desligado às 22h, o backup roda assim que
ele voltar. O backup depende do Docker Desktop aberto (e do OneDrive para subir
para a nuvem), e os dois só rodam com alguém logado no Windows: mais um motivo
para o login automático do passo 1.

**Confira de vez em quando** se há arquivos novos na pasta de backups. Um backup
que parou de rodar não avisa ninguém.

## 8. Atualizar para uma versão nova

O PC de casa usa **sempre a branch `master`**, que só recebe código já validado.
O desenvolvimento acontece na branch `develop`; depois de testada, ela é mesclada
na `master` e enviada para o GitHub. Só então atualize aqui:

```powershell
cd C:\bicavi
git log -1 --oneline   # anote o commit atual: é para ele que você volta se der problema
powershell -ExecutionPolicy Bypass -File .\scripts\backup.ps1 -Destino "$env:USERPROFILE\OneDrive\Bicavi-backups"
git pull
docker compose pull db         # baixa a versão mais nova do Postgres 17 (correções de segurança)
docker compose build --pull    # recompila o app sobre as imagens base mais novas (Java, Node, nginx)
docker compose up -d
```

Por que `pull` e `--pull`: o Docker reaproveita as imagens que já estão no PC.
Sem eles, as correções de segurança do Postgres, do Java e do nginx nunca
chegariam aqui. A tag `postgres:17` só recebe versões 17.x, que são
compatíveis com os dados já gravados; a troca para outra versão principal
(18, por exemplo) exige migrar os dados e nunca acontece sozinha.

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

3. Quando a correção chegar na `master`, volte para ela e atualize com os
   mesmos comandos do início desta seção (backup antes):

   ```powershell
   git switch master
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
| Parou de abrir depois de uma noite | PC reiniciou (Windows Update) e ninguém entrou no Windows: o Docker Desktop não abriu |
| Funciona no Wi-Fi mas não no 4G | VPN do Tailscale desligada no iPhone (ative o VPN On Demand), ou o app Tailscale sem permissão de usar dados móveis |
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

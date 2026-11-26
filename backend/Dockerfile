# Imagem do backend em dois estágios ("multi-stage build"):
#  1. build: JDK + Maven Wrapper compilam e geram o .jar
#  2. runtime: só o JRE e o .jar. Sem JDK, Maven nem código-fonte: imagem
#     menor e com menos coisa que um invasor poderia usar.
#
# Imagens pela tag (21-jdk, 21-jre), e não pelo digest: assim o "build --pull"
# traz as correções de segurança do Java (ver DEPLOY.md, seção 8).

# ---------- 1. build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Primeiro só o que define as dependências. O Docker guarda cada etapa em cache:
# enquanto o pom.xml não mudar, o download das dependências não se repete
# (mudar só o código Java reaproveita esta etapa).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# -DskipTests: os testes usam Testcontainers (precisam de Docker), que não existe
# dentro deste build. Eles rodam antes, no desenvolvimento (./mvnw test).
RUN ./mvnw -B -q package -DskipTests

# ---------- 2. runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Usuário sem privilégios: se a aplicação for comprometida, o invasor não é root.
RUN useradd --system --uid 1001 bicavi
COPY --from=build /app/target/*.jar app.jar
USER bicavi

EXPOSE 8080
# Memória. O limite do container é 768m (mem_limit no compose.yaml), e a JVM
# usa memória em duas partes:
#  - heap: os objetos do app. Medido sob carga: ~80 MiB em uso.
#  - todo o resto (classes do Spring/Hibernate, código compilado pelo JIT,
#    threads...): ~220 MiB, medido com Native Memory Tracking (nov/2026).
#
# MaxRAMPercentage=60: o heap pode crescer até 60% do limite (~460 MiB), bem
# mais do que o app usa. Com 75% (~576 MiB), um pico de heap somado aos ~220 MiB
# do resto passaria dos 768m: o Docker mataria o processo na hora (OOMKilled),
# sem deixar nenhuma mensagem no log. Com 60%, sobra folga para o resto.
#
# ExitOnOutOfMemoryError: se mesmo assim o heap acabar, a JVM escreve o erro no
# log e encerra; o "restart: unless-stopped" do compose sobe o app de novo.
# Sem ele, o app poderia continuar rodando pela metade (o erro mata só a
# thread que estava trabalhando, e as outras seguem num estado desconhecido).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]

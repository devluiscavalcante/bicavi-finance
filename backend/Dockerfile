# Imagem do backend em dois estágios ("multi-stage build"):
#  1. build: JDK + Maven Wrapper compilam e geram o .jar
#  2. runtime: só o JRE e o .jar. Sem JDK, Maven nem código-fonte: imagem
#     menor e com menos coisa que um invasor poderia usar.

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
# MaxRAMPercentage: a JVM respeita a memória do container em vez da do PC inteiro.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

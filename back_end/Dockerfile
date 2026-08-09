# 构建阶段（Java 17）
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# 利用缓存加速构建
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

# 运行阶段
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar ./app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
#COPY . .
#CMD ["mvn", "spring-boot:run"]

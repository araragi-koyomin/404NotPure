# 构建阶段（Java 8）
FROM maven:3.8.6-openjdk-8 AS build
WORKDIR /app
COPY pom.xml .
# 利用缓存加速构建
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

# 运行阶段（使用更小的JRE镜像）
FROM openjdk:8-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar ./app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
#COPY . .
#CMD ["mvn", "spring-boot:run"]
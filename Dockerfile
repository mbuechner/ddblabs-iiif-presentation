FROM maven:3-jdk-12-alpine AS MAVEN_CHAIN
COPY pom.xml /tmp/
COPY src /tmp/src/
WORKDIR /tmp/
RUN mvn package

FROM openjdk:12-alpine
RUN mkdir /home/iiif-presentation
COPY --from=MAVEN_CHAIN /tmp/target/iiif-presentation.jar /home/iiif-presentation/iiif-presentation.jar
WORKDIR /home/iiif-presentation/
CMD ["java", "-jar", "iiif-presentation.jar"]

EXPOSE 80
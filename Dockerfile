FROM maven:3-openjdk-14 AS MAVEN_CHAIN
MAINTAINER Michael Büchner <m.buechner@dnb.de>
COPY pom.xml /tmp/
COPY src /tmp/src/
WORKDIR /tmp/
RUN mvn package

FROM 14-jdk-slim-buster
MAINTAINER Michael Büchner <m.buechner@dnb.de>
RUN mkdir /home/iiif-presentation
COPY --from=MAVEN_CHAIN /tmp/target/iiif-presentation.jar /home/iiif-presentation/iiif-presentation.jar
WORKDIR /home/iiif-presentation/
CMD ["java", "-jar", "iiif-presentation.jar"]

EXPOSE 80
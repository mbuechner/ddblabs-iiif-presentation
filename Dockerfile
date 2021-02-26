FROM maven:3-openjdk-15-slim AS MAVEN_CHAIN
MAINTAINER Michael Büchner <m.buechner@dnb.de>
COPY pom.xml /tmp/
COPY src /tmp/src/
WORKDIR /tmp/
RUN mvn package

FROM openjdk:15-slim
MAINTAINER Michael Büchner <m.buechner@dnb.de>
RUN mkdir /home/iiif-presentation
RUN mkdir /tmp/xdgconfig
RUN chmod 777 /tmp/xdgconfig/
ENV XDG_CONFIG_HOME /tmp/xdgconfig/ 
COPY --from=MAVEN_CHAIN /tmp/target/iiif-presentation.jar /home/iiif-presentation/iiif-presentation.jar
WORKDIR /home/iiif-presentation/
CMD ["java", "-Xms512M", "-Xmx1G", "-jar", "iiif-presentation.jar"]

EXPOSE 8080


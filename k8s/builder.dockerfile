FROM docker.io/maven:3-eclipse-temurin-17
WORKDIR /tmp/app

# Speed up builds by verifying poms before build (cache mvn downloads)
ADD ./pom.xml /tmp/app/pom.xml
ADD ./common/pom.xml /tmp/app/common/pom.xml
ADD ./ball/pom.xml /tmp/app/ball/pom.xml
ADD ./player/pom.xml /tmp/app/player/pom.xml
ADD ./stadium/pom.xml /tmp/app/stadium/pom.xml
ADD ./ui/pom.xml /tmp/app/ui/pom.xml
RUN mvn verify --fail-never

ADD ./common/src /tmp/app/common/src
ADD ./ball/src /tmp/app/ball/src
ADD ./player/src /tmp/app/player/src
ADD ./stadium/src /tmp/app/stadium/src
ADD ./ui/src /tmp/app/ui/src
RUN mvn package dependency:copy-dependencies

RUN ls -al /tmp/app/ui/

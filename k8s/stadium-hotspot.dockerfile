FROM adoptopenjdk/openjdk11:alpine-slim

EXPOSE 8080

# Copy dependencies
COPY stadium/target/dependency/* /deployment/libs/

# Copy classes
COPY stadium/target/classes /deployment/classes

RUN chgrp -R 0 /deployment && chmod -R g+rwX /deployment

CMD java -Dvertx.disableDnsResolver=true -cp /deployment/classes:/deployment/libs/* demo.mesharena.stadium.MainKt

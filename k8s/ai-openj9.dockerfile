FROM adoptopenjdk/openjdk11-openj9:alpine-slim

EXPOSE 8080

# Copy dependencies
COPY ai/target/dependency/* /deployment/libs/

# Copy classes
COPY ai/target/classes /deployment/classes

RUN chgrp -R 0 /deployment && chmod -R g+rwX /deployment

CMD java -Dvertx.disableDnsResolver=true -cp /deployment/classes:/deployment/libs/* demo.mesharena.ai.MainKt

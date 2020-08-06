FROM adoptopenjdk/openjdk11:alpine-slim

EXPOSE 8080

# Copy dependencies
COPY ui/target/dependency/* /deployment/libs/

# Copy classes
COPY ui/target/classes /deployment/classes

RUN chgrp -R 0 /deployment && chmod -R g+rwX /deployment

CMD java -Dvertx.disableDnsResolver=true -cp /deployment/classes:/deployment/libs/* demo.mesharena.ui.UI

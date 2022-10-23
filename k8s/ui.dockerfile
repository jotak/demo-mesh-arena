FROM mesharena-builder as builder
FROM docker.io/eclipse-temurin:17-alpine

# Copy libs & classes
COPY --from=builder /tmp/app/ui/target/libs/* /deployment/libs/
COPY --from=builder /tmp/app/ui/target/classes /deployment/classes

RUN chgrp -R 0 /deployment && chmod -R g+rwX /deployment

CMD java -Dvertx.disableDnsResolver=true -cp /deployment/classes:/deployment/libs/* UIVerticle

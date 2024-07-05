FROM localhost/mesharena-builder as builder
FROM docker.io/eclipse-temurin:17-jre

# Copy libs & classes
COPY --from=builder /tmp/app/ball/target/libs/* /deployment/libs/
COPY --from=builder /tmp/app/ball/target/classes /deployment/classes

RUN chgrp -R 0 /deployment && chmod -R g+rwX /deployment

USER 9000:9000

CMD java -Dvertx.disableDnsResolver=true -cp /deployment/classes:/deployment/libs/* BallVerticle

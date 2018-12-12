FROM fabric8/java-alpine-openjdk8-jre

EXPOSE 8080 9090

# Copy dependencies
COPY target/dependency/* /deployment/libs/

# Copy classes
COPY target/classes /deployment/classes

ENV JAVA_APP_DIR=/deployment
ENV JAVA_LIB_DIR=/deployment/libs
ENV JAVA_CLASSPATH=${JAVA_APP_DIR}/classes:${JAVA_LIB_DIR}/*
ENV JAVA_MAIN_CLASS="demo.mesharena.ui.UI"
RUN chgrp -R 0 ${JAVA_APP_DIR} && chmod -R g+rwX ${JAVA_APP_DIR}

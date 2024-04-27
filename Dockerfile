FROM eclipse-temurin:8-jre-jammy

MAINTAINER Open Identity Platform Community <open-identity-platform-openidm@googlegroups.com>

ENV USER="openicf"
ENV OPENICF_OPTS="-server -XX:+UseContainerSupport"

ARG VERSION

WORKDIR /opt

#COPY OpenICF-java-framework/openicf-zip/target/*.zip ./

RUN apt-get update
RUN apt-get install -y --no-install-recommends curl unzip
RUN if [ ! -z "$VERSION" ] ; then rm -rf ./*.zip ; curl -L https://github.com/OpenIdentityPlatform/OpenICF/releases/download/$VERSION/openicf-$VERSION.zip --output openicf-$VERSION.zip ; fi
RUN unzip openicf-*.zip && rm -rf *.zip
RUN apt-get remove -y --purge unzip
RUN rm -rf /var/lib/apt/lists/*
RUN groupadd $USER
RUN useradd -m -r -u 1001 -g $USER $USER
RUN install -d -o $USER /opt/openicf
RUN chown -R $USER:$USER /opt/openicf
RUN chmod -R g=u /opt/openicf
RUN chmod +x /opt/openicf/bin/*.sh

EXPOSE 8759

USER $USER

HEALTHCHECK --interval=30s --timeout=30s --start-period=1s --retries=3 CMD curl -i -o - --silent  http://127.0.0.1:8759/openicf | grep -q "OpenICF0 Connector Server"

ENTRYPOINT ["/opt/openicf/bin/ConnectorServer.sh","/run"]

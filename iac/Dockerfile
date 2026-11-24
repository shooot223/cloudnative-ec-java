FROM node:24-bookworm AS cli
ARG CDK_CLI_VERSION=2.1149.0
RUN npm install --global --no-audit --no-fund aws-cdk@${CDK_CLI_VERSION}

FROM amazoncorretto:21-al2023-jdk
COPY --from=cli /usr/local/bin/node /usr/local/bin/node
COPY --from=cli /usr/local/lib/node_modules/aws-cdk /opt/aws-cdk
ARG IAC_UID=1000
ARG IAC_GID=1000
ENV HOME=/home/cdk GRADLE_USER_HOME=/home/cdk/.gradle
RUN ln -s /opt/aws-cdk/bin/cdk /usr/local/bin/cdk \
    && mkdir -p /home/cdk/.gradle /workspace/iac \
    && (getent group "${IAC_GID}" >/dev/null || echo "cdk:x:${IAC_GID}:" >> /etc/group) \
    && (getent passwd "${IAC_UID}" >/dev/null || echo "cdk:x:${IAC_UID}:${IAC_GID}:CDK:/home/cdk:/bin/sh" >> /etc/passwd) \
    && chown -R "${IAC_UID}:${IAC_GID}" /home/cdk /workspace/iac \
    && java -version && node --version && cdk --version
USER ${IAC_UID}:${IAC_GID}
WORKDIR /workspace/iac
CMD ["cdk", "--version"]

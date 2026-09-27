FROM m.daocloud.io/docker.io/library/tomcat:10.1-jdk17-temurin
RUN rm -rf /usr/local/tomcat/webapps/ROOT
COPY target/usamis.war /usr/local/tomcat/webapps/usamis.war
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8080/usamis/api/health || exit 1
CMD ["catalina.sh", "run"]

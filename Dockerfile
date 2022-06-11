FROM java
VOLUME /
ADD ./snow-admin.jar app.jar
EXPOSE 8081
ENTRYPOINT [ "java", "-jar", "/app.jar" ]

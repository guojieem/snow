FROM java
VOLUME /tmp
ADD ./snow-admin.jar snow-admin.jar
EXPOSE 8081
ENTRYPOINT [ "java", "-jar", "/snow-admin.jar" ]

FROM java:8-jre-alpine

COPY target/scala-2.12/content-camera-service.jar /

EXPOSE 8080

CMD java -jar content-camera-service.jar

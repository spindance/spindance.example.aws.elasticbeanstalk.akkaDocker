FROM java:8-jre-alpine

COPY target/scala-2.12/image-grouper.jar /

EXPOSE 8080

CMD java -jar image-grouper.jar

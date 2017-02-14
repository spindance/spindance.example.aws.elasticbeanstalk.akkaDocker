sbt assembly

docker build -t cc-service .

docker run -it -p 8080:8080 --rm --name my-runnning-cc-service cc-service

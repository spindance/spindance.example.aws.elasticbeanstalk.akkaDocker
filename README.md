sbt assembly

docker build -t image-grouper .

docker run -it -p 8080:8080 --rm --name my-runnning-image-grouper image-grouper

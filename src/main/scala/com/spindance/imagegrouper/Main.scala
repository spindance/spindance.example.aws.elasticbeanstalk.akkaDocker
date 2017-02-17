package com.spindance.imagegrouper

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Main extends App with StrictLogging {

  type GroupId = String
  type ImageId = String

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  // In memory storage of JPEG images. Not recommended for production!
  var imageMap: Map[GroupId, Map[ImageId, ByteString]] = Map()

  //  GET: http://hostname/images/GROUP_ID
  //  GET: http://hostname/images/GROUP_ID/IMAGE_ID
  //  POST: http://hostname/images/GROUP_ID/IMAGE_ID

  val route: Route =
    pathSingleSlash {
      complete("Server is running...\n")
    } ~
    path("images" / Segment) { (groupId: GroupId) =>
      get {
        val maybeImageHtmlElements = imageMap get groupId map (_.keys) map { (imageIds) => imageIds map (imageId => imageDisplay(groupId, imageId)) }

        maybeImageHtmlElements match {
          case Some(imageHtmlElements) =>
            val htmlBody = imageHtmlElements.fold("") { (acc, imageHtmlElement) => acc + imageHtmlElement }
            complete(
              HttpResponse(
                status = StatusCodes.OK,
                entity = HttpEntity(
                  ContentTypes.`text/html(UTF-8)`,
                  s"<html><body>$htmlBody</body></html>"
                )
              )
            )
          case None => complete(HttpResponse(status = StatusCodes.NotFound))
        }
      }
    } ~
    path("images" / Segment / Segment) { (groupId: GroupId, imageId: ImageId) =>
      get {
        imageMap get groupId flatMap (_ get imageId) match {
          case Some(image) => complete(HttpResponse(status = StatusCodes.OK, entity = HttpEntity(contentType = MediaTypes.`image/jpeg`, data = image)))
          case None => complete(HttpResponse(status = StatusCodes.NotFound))
        }
      } ~
      post {
        extractRequest { req =>
          logger.info(s"Receiving Image Data: $req")

          // We are just going to wait for the entire image to be transferred into RAM. This might not be a great idea
          // in a production environment. It might be better to just stream the image data to the disk.
          val strictEntity = req.entity.toStrict(60 seconds)

          val response = strictEntity map { se =>
            logger.info(s"Finished receiving data, Strict request: $se")
            val existingImageSet = imageMap getOrElse (groupId, Map())
            val newImageSet = existingImageSet + (imageId -> se.data)

            // This is so many levels of wrong, DO NOT do this in a production environment, ever!
            // Changes to the imageMap NEED to be synchronized or lost data is inevitable. Works for POC
            imageMap = imageMap + (groupId -> newImageSet)

            s"successfully received image from $groupId, with image id of $imageId"
          }

          complete (response)
        }
      }
    }

  def imageDisplay(groupId: GroupId, imageId: ImageId): String = {
    s"""
       |<p><img src="$groupId/$imageId"></p>
       |<p>Image at: $groupId/$imageId</p>
    """.stripMargin
  }

  val loggedRoute = DebuggingDirectives.logRequestResult("http-logs") {route}
  val bindingFuture = Http().bindAndHandle(loggedRoute, "0.0.0.0", 8080)

  bindingFuture.onComplete {
    case Success(result) =>
      logger.info(s"Successful binding: $result")

    case Failure(result) =>
      logger.error(s"Failed to bind! $result")
      shutdown(system)
  }

  sys.addShutdownHook {
    logger.info("Request for shutdown received!")
    shutdown(system)
  }

  def shutdown(system: ActorSystem): Unit = {
    logger.info("Gracefully shutting down!")
    bindingFuture.flatMap(_.unbind()).onComplete { _ =>
      system.terminate()
      logger.info("Shutdown complete")
    }
  }
}

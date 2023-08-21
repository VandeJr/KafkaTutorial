package com.vandejr
package bank.app

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import bank.actors.Bank
import bank.actors.PersistentBankAccount.Command

import akka.http.scaladsl.Http
import akka.util.Timeout
import com.vandejr.bank.http.BankRouter

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

object BankApp {

  def startHttpServer(bank: ActorRef[Command])(implicit system: ActorSystem[_]): Unit = {
    val router = new BankRouter(bank)
    val routes = router.routes

    implicit val ec: ExecutionContext = system.executionContext

    val httpBindingFuture = Http().newServerAt("localhost", 8080)
      .bind(routes)

    httpBindingFuture.onComplete {
      case Success(binding) =>
        val addr = s"${binding.localAddress.getHostString}:${binding.localAddress.getPort}"
        system.log.info(s"Server online at http://${addr}")
      case Failure(ex) =>
        system.log.error(s"Failed to bind HTTP server: ${ex}")
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    trait RootCommand
    case class RetrieveBankActor(replyTo: ActorRef[ActorRef[Command]]) extends RootCommand

    val rootBehavior: Behavior[RootCommand] = Behaviors.setup { context =>
      val bankActor = context.spawn(Bank(), "bank")

      Behaviors.receiveMessage {
        case RetrieveBankActor(replyTo) =>
          replyTo ! bankActor
          Behaviors.same
      }
    }

    implicit val system: ActorSystem[RootCommand] =
      ActorSystem(rootBehavior, "BankSystem")

    implicit val timeout: Timeout = Timeout(5.seconds)

    val bankActorFuture: Future[ActorRef[Command]] =
      system.ask((replyTo => RetrieveBankActor(replyTo)))

    implicit val ec: ExecutionContext = system.executionContext

    bankActorFuture.foreach(startHttpServer)
  }
}

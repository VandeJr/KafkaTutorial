package com.vandejr
package bank.http

import bank.actors.PersistentBankAccount.Command._
import bank.actors.PersistentBankAccount.{Command, Response}

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.concurrent.duration._

case class BankAccountCreationRequest(user: String, currency: String, balance: Double) {
  def toCommand(replyTo: ActorRef[Response]): Command =
    CreateBankAccount(user, currency,  balance, replyTo)
}

case class BankAccountUpdateRequest(currency: String, amount: Double) {
  def toCommand(id: String, replyTo: ActorRef[Response]): Command =
    UpdateBalance(id, currency, amount, replyTo)
}

case class FailureResponse(reason: String)

class BankRouter(bank: ActorRef[Command])(implicit system: ActorSystem[_]){

  implicit val timeout: Timeout = Timeout(5.seconds)

  /*
  * POST /bank/
  *   Payload: bank account creation request as JSON
  *   Response:
  *     201 Created
  *     Location: /bank/uuid
  *
  * GET /bank/uuid
  *   Response:
  *     200 OK
  *       JSON representation of bank account details
  *     404 Not Found
  *
  * PUT /bank/uuid
  *   Payload: new bank account details. (currency, amount) as JSON
  *   Response:
  *     200 OK
  *       Payload: new bank account as JSON
  *     404 Not Found
  *     400 Bad Request
  * */

  def createBankAccount(request: BankAccountCreationRequest): Future[Response] =
    bank.ask(replyTo => request.toCommand(replyTo))

  def getBankAccount(id: String): Future[Response] =
    bank.ask(replyTo => GetBankAccount(id, replyTo))

  def updateBankAccount(id: String, request: BankAccountUpdateRequest): Future[Response] =
    bank.ask(replyTo => request.toCommand(id, replyTo))

  val routes =
    pathPrefix("bank") {
      pathEndOrSingleSlash {
        post {
          entity(as[BankAccountCreationRequest]) { request =>
            onSuccess(createBankAccount(request)) {
              case Response.BankAccountCreatedResponse(id) =>
                respondWithHeader(Location(s"/bank/$id")) {
                  complete(StatusCodes.Created)
                }
            }
          }
        }
      } ~ path(Segment) { id =>
        get {
          onSuccess(getBankAccount(id)) {
            case Response.GetBankAccountResponse(Some(account)) =>
              complete(account)
            case Response.GetBankAccountResponse(None) =>
              complete(StatusCodes.NotFound, FailureResponse(s"Bank account with $id not founded"))
          }
        } ~
        put {
          entity(as[BankAccountUpdateRequest]) { request =>
            onSuccess(updateBankAccount(id, request)) {
              case Response.BalanceUpdatedResponse(Some(account)) =>
                complete(account)
              case Response.BalanceUpdatedResponse(None) =>
                complete(StatusCodes.NotFound, FailureResponse(s"Bank account with $id not founded"))
            }
          }
        }
      }
    }

}

package astrac.akka.askretry

import akka.actor._
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.Future

object RetryingActor {
  case class Ask[T](target: ActorRef, message: T, rate: FiniteDuration, maxAttempts: Int)
  case object Retry

  case class RetryException(attempts: Int) extends Exception(s"Cannot retry after $attempts attempts")

  def props[T] = Props[RetryingActor]
}

class RetryingActor extends Actor
{
  import RetryingActor._

  var attempts = 0

  def retrying[T](requester: ActorRef, subscription: Cancellable, target: ActorRef, message: T, rate: FiniteDuration, maxAttempts: Int): Receive = {
    case Retry =>
      if (attempts < maxAttempts) target ! message
      else {
        requester ! Status.Failure(RetryException(attempts))
        subscription.cancel()
        context.stop(self)
      }
    case response =>
      requester ! response
      subscription.cancel()
      context.stop(self)
  }

  def receive: Receive = {
    case Ask(target, message, rate, maxAttempts) =>
      val subscription = context.system.scheduler.schedule(0.second, rate, self, Retry)(context.system.dispatcher)
      context.become(retrying(sender, subscription, target, message, rate, maxAttempts))
  }
}

object AskRetryPattern {
  def retry[T](actor: ActorRef, msg: T, maxAttempts: Int, rate: FiniteDuration)(implicit context: ActorContext): Future[Any] = {

    implicit val to = Timeout.durationToTimeout((rate * maxAttempts) + 1.millis)

    context.actorOf(RetryingActor.props) ? RetryingActor.Ask(actor, msg, rate, maxAttempts)
  }

  implicit class RetryingActorRef(val ref: ActorRef) extends AnyVal {
    def askretry[T, R](
      msg: T, maxAttempts: Int, rate: FiniteDuration)(implicit context: ActorContext): Future[Any] =
        retry(ref, msg, maxAttempts, rate)
  }
}

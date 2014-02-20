package astrac.akka.askretry

import akka.actor._
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.Future

/**
 * Companion object for the RetryingActor containing utility definitions
 */
private object RetryingActor {
  def props[T] = Props[RetryingActor]
}

/**
 * An actor that retries sending the same message until it receive a response
 * or until it reaches a limit of attempts.
 */
private class RetryingActor extends Actor
{
  // Import message classes
  import AskRetry._
  // Make an execution context available as the scheduler needs it
  implicit val ec = context.system.dispatcher
  // Keep count of attempts
  var attempts = 0

  /**
   * Wait for an Ask message containing the instructions for the actor task
   * @return Unit
   */
  def receive: Receive = {
    // In case we received an Ask invocation
    case Ask(target, message, rate, maxAttempts) =>
      // Create a cancellable subscription by scheduling the dispatch of a Retry
      // message to this actor accordingly to the parameters of the Ask message
      val subscription = context.system.scheduler.schedule(0.second, rate, self, Retry)
      // Switch behaviour passing along useful information to complete the task
      context.become(retrying(subscription, sender, target, message, maxAttempts))
  }

  /**
   * Keep retrying until the response is received or the maximum number of
   * attempts has been performed
   *
   * @param subscription Cancellable Subscription that can cancel the scheduled message
   * @param requester ActorRef The actor that originated the request being served
   * @param target ActorRef The actor that should respond to the ask request
   * @param message T The message to be delivered in the ask request
   * @param maxAttempts Int The maximum number that the request should be tried
   * @return Unit
   */
  def retrying[T](subscription: Cancellable, requester: ActorRef, target: ActorRef, message: T, maxAttempts: Int): Receive = {
    // In case the actor receives a scheduled message...
    case Retry =>
      // and if there are still attempts left
      if (attempts < maxAttempts) {
        // increase the attempts counter
        attempts = attempts + 1
        // try sending a message to the target
        target ! message
      } else {
        // if we can't do any other attempt let the requester know of the failure
        requester ! Status.Failure(RetryException(attempts))
        // cancel the scheduled message
        subscription.cancel()
        // stop the actor
        context.stop(self)
      }
      // In case we receive any other message, since this actor is hidden, it
      // can only be the response
    case response =>
      // Send it back to the original requester
      requester ! response
      // cancel the scheduled message
      subscription.cancel()
      // stop the actor
      context.stop(self)
  }
}

/**
 * An object to import to make the askretry extension available on ActorRef
 */
object AskRetry {
  /**
   * Case class for the message that is used to start the RetryingActor routine
   */
  private[askretry] case class Ask[T](target: ActorRef, message: T, rate: FiniteDuration, maxAttempts: Int)
  /**
   * Case object for the message that will trigger a new attempt on the ask pattern
   */
  private[askretry] case object Retry
  /**
   * Class that represent a failure in delivering the message and retrieving an
   * answer in a timely manner
   */
  case class RetryException(attempts: Int) extends Exception(s"Cannot retry after $attempts attempts")

  /**
   * Utility method to initiate the RetryingActor
   */
  private def retry[T](actor: ActorRef, msg: T, maxAttempts: Int, rate: FiniteDuration)(implicit context: ActorContext): Future[Any] = {
    // Sets a proper timeout for the ask request
    implicit val to = Timeout.durationToTimeout(rate * (maxAttempts + 1))
    // Starts a retry actor and use the ask pattern to get the response future
    context.actorOf(RetryingActor.props) ? Ask(actor, msg, rate, maxAttempts)
  }

  /**
   * Implicit conversion to extend an ActorRef with the askretry method
   * @param ref ActorRef The ref to be wrapped for extension
   */
  implicit class RetryingActorRef(val ref: ActorRef) extends AnyVal {
    /**
     * Sends a maximum number of times at the given rate expecting a response
     * and providing a Future that will contain it
     *
     * @param msg T The message to be sent
     * @param maxAttempts Int The maximum number of attempts to be performed
     * @param rate FiniteDuration The period of the attempts
     * @param context ActorContext An actor context implicitly available
     * @return Future[Any] A future of the message coming as a response
     */
    def askretry[T](
      msg: T, maxAttempts: Int, rate: FiniteDuration)(implicit context: ActorContext): Future[Any] =
        retry(ref, msg, maxAttempts, rate)
  }
}

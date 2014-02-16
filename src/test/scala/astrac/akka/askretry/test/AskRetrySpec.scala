package astrac.akka.askretry.test

import akka.testkit.{TestProbe, TestKit, ImplicitSender}
import akka.actor.{Props, ActorRef, Actor, ActorSystem}
import org.scalatest.{FunSuiteLike, Matchers, BeforeAndAfterAll}
import astrac.akka.askretry.AskRetry._
import scala.concurrent.duration._
import akka.pattern.pipe
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await

class AskRetryTestActor(target: ActorRef) extends Actor {
  implicit val ec = context.system.dispatcher

  def receive: Actor.Receive = {
    case "ASK" => target.askretry("MSG", 5, 200.millis).pipeTo(sender)
  }
}

class AskRetrySuite(_system: ActorSystem) extends TestKit(_system) with FunSuiteLike with Matchers with BeforeAndAfterAll with ImplicitSender
{
  def this() = this(ActorSystem("AskRetrySuite"))

  implicit val t = Timeout(200.millis)
  implicit val ec = system.dispatcher

  val target = TestProbe()
  val source = system.actorOf(Props(classOf[AskRetryTestActor], target.ref))

  override def afterAll: Unit = system.shutdown()

  test("An ask-retry request should be served correctly if the messages are delivered at the first attempt") {
    val f = source ? "ASK"
    target.expectMsg("MSG")
    target.reply("OK")
    Await.result(f, 100.millis) should equal("OK")
  }

  test("An ask-retry request should retry the specified number of times before failing") {
    val f = source ? "ASK"
    target.expectMsg("MSG")
    target.expectMsg(210.millis, "MSG")
    target.expectMsg(400.millis, "MSG")
    target.expectMsg(600.millis, "MSG")
    target.expectMsg(800.millis, "MSG")
    target.reply("OK")
    Await.result(f, 900.millis) should equal("OK")
  }

  test("An ask-retry request should retry the specified number of times and then fail") {
    pending
  }
}

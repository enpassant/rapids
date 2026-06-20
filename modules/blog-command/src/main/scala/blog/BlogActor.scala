package blog

import common._

import org.apache.pekko.actor._
import org.apache.pekko.persistence._

object BlogActor {
  def props(id: String) = Props(new BlogActor(id))
}

class BlogActor(val id: String) extends CommandActor {

  override def persistenceId = s"blog-$id"

  var state: Option[Blog] = None

  def updateState(event: BlogMessage): Unit = event match {
    case BlogCreated(_, _, _, title, content, _) =>
      state = Some(Blog(title, content))
    case BlogModified(_, _, _, title, content) =>
      state = Some(Blog(title, content))
    case DiscussionStarted(id, _, _, _, _, title) =>
      state = state map { blog =>
        blog.copy(discussions = DiscussionItem(id, title) :: blog.discussions)
      }
    case _ =>
  }

  val receiveRecover: Receive = {
    case event: BlogMessage =>  updateState(event)
    case SnapshotOffer(_, snapshot: Blog) => state = Some(snapshot)
  }

  val processCommand: Receive = {
    case "snap"  => saveSnapshot(state)
    case CreateBlog(title, content, datetime, loggedIn) if !state.isDefined && datetime != null =>
      val payload = CommonUtil.extractPayload(loggedIn.token)
      payload foreach { p =>
        val event = BlogCreated(id, loggedIn.userId, loggedIn.userName, title, content, datetime)
        persistAsync(event) {
          event =>
            sender() ! event
            updateState(event)
        }
      }
    case ModifyBlog(title, content, loggedIn) if state.isDefined =>
      val payload = CommonUtil.extractPayload(loggedIn.token)
      payload foreach { p =>
        val event = BlogModified(id, loggedIn.userId, loggedIn.userName, title, content)
        persistAsync(event) {
          event =>
            sender() ! event
            updateState(event)
        }
      }
    case event: DiscussionStarted =>
      persistAsync(event) {
        event =>
          sender() ! event
          updateState(event)
      }
    case msg =>
      sender() ! WrongMessage(msg.toString)
  }
}


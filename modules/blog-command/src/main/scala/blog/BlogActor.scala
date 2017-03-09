package blog

import common._

import akka.actor._
import akka.persistence._
import com.mongodb.casbah.commons.Imports._

object BlogActor {
	def props(id: String) = Props(new BlogActor(id))
}

class BlogActor(val id: String) extends Actor with PersistentActor {
	import BlogActor._
	import common.TypeHintContext._

  override def persistenceId = s"blog-$id"

  var state: Option[Blog] = None

  def updateState(event: BlogMessage): Unit = event match {
    case BlogCreated(id, userId, userName, title, content) =>
      state = Some(Blog(title, content))
    case DiscussionStarted(id, blogId, title) =>
      state = state map { blog =>
        blog.copy(discussions = DiscussionItem(id, title) :: blog.discussions)
      }
    case _ =>
  }

  val receiveRecover: Receive = {
    case event: BlogMessage =>	updateState(event)
    case SnapshotOffer(_, snapshot: Blog) => state = Some(snapshot)
  }

  val receiveCommand: Receive = {
    case "snap"  => saveSnapshot(state)
    case CreateBlog(title, content, loggedIn) if !state.isDefined =>
      val payload = CommonUtil.extractPayload(loggedIn.token)
      payload foreach { p =>
        val event = BlogCreated(id, p.sub, p.name, title, content)
        persistAsync(event) {
          event =>
            sender ! event
            updateState(event)
        }
			}
    case event: DiscussionStarted =>
      persistAsync(event) {
				event =>
					sender ! event
					updateState(event)
			}
    case msg =>
      sender ! WrongMessage(msg.toString)
  }
}


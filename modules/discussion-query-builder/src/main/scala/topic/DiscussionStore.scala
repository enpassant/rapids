package discussion

import blog._

trait DiscussionStore {
  def insert(discussionStarted: DiscussionStarted): Unit

  def addComment(commentAdded: CommentAdded, htmlContent: String): Unit

  def replayComment(commentReplied: CommentReplied, htmlContent: String): Unit
}


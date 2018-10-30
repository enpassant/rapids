package discussion

import blog._

trait DiscussionStore {
  def insert(discussionStarted: DiscussionStarted): Unit

  def addComment(commentAdded: CommentAdded): Unit

  def replayComment(commentReplied: CommentReplied): Unit
}


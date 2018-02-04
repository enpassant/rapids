package discussion

import blog._

trait DiscussionStore {
  def insert(discussionStarted: DiscussionStarted)

  def addComment(commentAdded: CommentAdded)

  def replayComment(commentReplied: CommentReplied)
}


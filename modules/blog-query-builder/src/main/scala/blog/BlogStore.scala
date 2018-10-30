package blog

trait BlogStore {
  def insert(blogCreated: BlogCreated, htmlContent: String): Unit

  def existsBlog(id: String): Boolean

  def update(blogModified: BlogModified, htmlContent: String): Unit

  def addDiscussion(discussionStarted: DiscussionStarted): Unit
}

package blog

trait BlogStore {
  def insert(blogCreated: BlogCreated, htmlContent: String)

  def existsBlog(id: String): Boolean

  def update(blogModified: BlogModified, htmlContent: String)

  def addDiscussion(discussionStarted: DiscussionStarted)
}

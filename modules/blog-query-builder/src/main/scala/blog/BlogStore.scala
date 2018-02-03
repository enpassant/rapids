package blog

trait BlogStore {
  def insert(
    id: String,
    userId: String,
    userName: String,
    title: String,
    content: String,
    htmlContent: String)

  def existsBlog(id: String): Boolean

  def update(
    id: String,
    title: String,
    content: String,
    htmlContent: String)

  def addDiscussion(
    blogId: String,
    id: String,
    userId: String, userName: String, title: String)
}

package discussion

trait DiscussionStore {
  def insert(
    id: String,
    userId: String,
    userName: String,
    blogId: String,
    title: String)

  def addComment(
    key: String,
    id: String,
    userId: String,
    userName: String,
    content: String)

  def replayComment(
    key: String,
    pos: String,
    id: String,
    userId: String,
    userName: String,
    content: String)
}


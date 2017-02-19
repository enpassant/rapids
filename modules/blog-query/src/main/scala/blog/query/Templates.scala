package blog.query

import com.github.jknack.handlebars.{ Context, Handlebars }
import fixiegrips.{ Json4sHelpers, Json4sResolver }

object Templates {
    val handlebars = new Handlebars().registerHelpers(Json4sHelpers)
    def ctx(obj: Object) =
      Context.newBuilder(obj).resolver(Json4sResolver).build

    val strBlogs = """
      |{{#each blogs}}
      |<h1>
      |  <a href="/query/blog/{{_id}}">{{title}}</a>
      |</h1>
      |{{/each}}""".stripMargin
    val blogs = handlebars.compileInline(strBlogs)
    def renderBlogs(obj: Object) = blogs(ctx(obj))

    val strBlog = """
      |<h1>
      |  {{title}}
      |</h1>
      |{{content}}""".stripMargin
    val blog = handlebars.compileInline(strBlog)
    def renderBlog(obj: Object) = blog(ctx(obj))
}

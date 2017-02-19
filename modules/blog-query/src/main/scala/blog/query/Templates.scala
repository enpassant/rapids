package blog.query

import com.github.jknack.handlebars.{ Context, Handlebars }
import fixiegrips.{ Json4sHelpers, Json4sResolver }

object Templates {
    val handlebars = new Handlebars().registerHelpers(Json4sHelpers)
    def ctx(obj: Object) =
      Context.newBuilder(obj).resolver(Json4sResolver).build

    val strBlogs = """
      |{{#each blogs}}
      |<div>
      |<h1 class="blog">
      |  <a href="/query/blog/{{_id}}">{{title}}</a>
      |</h1>
      |{{#each discussions}}
      |<span class="discussion">
      |  <a href="/query/discussion/{{_id}}">{{title}}</a>
      |</span>
      |{{/each}}
      |</div>
      |{{/each}}""".stripMargin
    val blogs = handlebars.compileInline(strBlogs)
    val renderBlogs = (obj: Object) => blogs(ctx(obj))

    val strBlog = """
      |<h1>
      |  {{title}}
      |</h1>
      |{{content}}""".stripMargin
    val blog = handlebars.compileInline(strBlog)
    val renderBlog = (obj: Object) => blog(ctx(obj))
}

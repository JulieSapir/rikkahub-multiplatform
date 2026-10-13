package me.rerere.rikkahub.web

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.resolveResource
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE

/**
 * 桌面端的内嵌 Web 服务：与上游 web/Entry.kt 同签名，只多一条显式的首页路由。
 *
 * 多的那条是必需的：上游让 staticResources 用 default("index.html") 兜住 "/"，而兜底只在
 * 「这条路解析不出内容」时才触发。GraalVM 原生镜像里目录本身也能 getResource 到一个可读的
 * resource:/static（一份目录清单），Ktor 对 resource 协议又不像 jar 那样先判 isFile，
 * 于是 "/" 把目录清单当正文返回，前端一个脚本都加载不到。这里直接把 "/" 指到 index.html，
 * 其余路径仍旧交给 staticResources，行为与 Android 端一致。
 */
fun startWebServer(
    port: Int = 8080,
    host: String = "0.0.0.0",
    module: suspend Application.() -> Unit
): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    return embeddedServer(CIO, port = port, host = host, module = {
        install(Compression)
        install(CORS) {
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowNonSimpleContentTypes = true
            anyHost()
            anyMethod()
        }
        install(SSE)
        install(DefaultHeaders)
        routing {
            get("/") {
                val index = call.resolveResource("index.html", "static")
                if (index != null) call.respond(index) else call.respondText("index.html", status = HttpStatusCode.NotFound)
            }
            staticResources("/", "static") {
                default("index.html")
                enableAutoHeadResponse()
                singlePageApplication()
            }
        }
        module()
    })
}

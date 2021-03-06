package eu.kanade.tachiyomi.extension.id.komikcast

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class KomikCast : WPMangaStream("Komik Cast", "https://komikcast.com", "id") {
    // Formerly "Komik Cast (WP Manga Stream)"
    override val id = 972717448578983812

    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/daftar-komik/page/$page/?order=popular", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/komik/page/$page/", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            val url = HttpUrl.parse("$baseUrl/page/$page")!!.newBuilder()
            val pattern = "\\s+".toRegex()
            val q = query.replace(pattern, "+")
            if (query.isNotEmpty()) {
                url.addQueryParameter("s", q)
            } else {
                url.addQueryParameter("s", "")
            }
            url.toString()
        } else {
            val url = HttpUrl.parse("$baseUrl/daftar-komik/page/$page")!!.newBuilder()
            var orderBy: String
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is StatusFilter -> url.addQueryParameter("status", arrayOf("", "ongoing", "completed")[filter.state])
                    is GenreListFilter -> {
                        val genreInclude = mutableListOf<String>()
                        filter.state.forEach {
                            if (it.state == 1) {
                                genreInclude.add(it.id)
                            }
                        }
                        if (genreInclude.isNotEmpty()) {
                            genreInclude.forEach { genre ->
                                url.addQueryParameter("genre[]", genre)
                            }
                        }
                    }
                    is SortByFilter -> {
                        orderBy = filter.toUriPart()
                        url.addQueryParameter("order", orderBy)
                    }
                }
            }
            url.toString()
        }
        return GET(url, headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.limit img").attr("src")
        element.select("div.bigor > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#readerarea img.size-full")
            .mapIndexed { i, img -> Page(i, "", img.attr("abs:Src")) }
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        SortByFilter(),
        Filter.Separator(),
        StatusFilter(),
        Filter.Separator(),
        GenreListFilter(getGenreList())
    )
}

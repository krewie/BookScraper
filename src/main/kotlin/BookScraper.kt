package org.krewie.bookScraper

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tongfei.progressbar.ProgressBar
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.Logger
import java.io.FileInputStream
import java.util.Properties
import java.util.concurrent.Executors
import org.slf4j.LoggerFactory

class PageScraper {

    private val urlCache = HashSet<String>()
    private val sharedMutex = Mutex()
    private val webClient = OkHttpClient()
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    companion object {
        private const val ROOT_PAGE = "http://books.toscrape.com/index.html"
    }
    private suspend fun fetchRequestAsync(url: String): Response = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()

        webClient.newCall(request).execute()
    }

    private suspend fun downloadPage(url: String, document: Document, targetDirectory: String) {
        val filename = url.substringAfterLast("/")
        val relativePath = getRelativePath(url, targetDirectory)

        val directory = File(relativePath)
        if (directory.mkdirs() || directory.exists()) {
            val filePath = File(directory, filename)
            filePath.writeText(document.toString())
            //place downloaded media on the root folder...
            downloadMedia(document, File(targetDirectory).toString())
        } else {
            logger.error("Failed to create directory: $directory")
        }
    }

    private suspend fun downloadMedia(document: Document, targetDirectory: String) {
        val elements = document.select("link[rel=stylesheet], img[src]")

        for (element in elements) {

            var resource = element.attr("abs:href")
            if (resource.isNullOrEmpty()) {
                resource = element.attr("abs:src")
            }

            if (!resource.isNullOrBlank()) {
                saveResource(
                    resource, getRelativePath(resource, targetDirectory)
                )
            }
        }
    }

    private suspend fun saveResource(url: String, targetDirectory: String) {
        try {
            val response = fetchRequestAsync(url)

            if (response.isSuccessful) {
                val filename = url.substringAfterLast("/")
                val filePath = File(targetDirectory, filename)
                try {
                    filePath.parentFile.mkdirs()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val fos = FileOutputStream(filePath)
                response.body?.bytes()?.let { fos.write(it) }
                fos.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getRelativePath(url: String, targetDirectory: String): String {
        val path = URI(url).path
        val relativePath = path.replace(Regex("/+"), "/").removePrefix("/").removeSuffix("/")
        return if (relativePath.contains("/")) {
            File(targetDirectory, relativePath.substringBeforeLast('/')).absolutePath
        } else {
            File(targetDirectory).absolutePath
        }
    }

    suspend fun scrape(
        dispatcher: ExecutorCoroutineDispatcher,
        baseUrl: String,
        depth: Int,
        targetDirectory: String,
        progressBar: ProgressBar
    ) {
        coroutineScope {
            try {
                val basePage = URI(baseUrl)

                if (isVisited(basePage.toString())) {
                    return@coroutineScope
                } else {
                    //mark page as visited
                    urlCache.add(basePage.toString())
                    progressBar.step()
                }

                val document: Document = Jsoup.connect(basePage.toString()).get()

                downloadPage(basePage.toString(), document, targetDirectory)

                if (depth > 0) {
                    val links: List<Element> = document.select("a[href]")

                    val jobList = links.filter { isValidUrl(it.attr("abs:href")) } // filter out logic...
                        .map { element ->
                            val nextUrl = element.attr("abs:href")
                            async(dispatcher) {
                                if (!isVisited(nextUrl)) {
                                    scrape(dispatcher, nextUrl, depth - 1, targetDirectory, progressBar)
                                }
                            }
                        }

                    jobList.awaitAll()
                }

            } catch (e: HttpStatusException) {
                when (e.statusCode) {
                    404 -> {
                        logger.error("Couldn't scrape: ${e.url}")
                    }

                    else -> {
                        logger.error("Unknown status code error when scraping page: ${e.url}")
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                logger.error("Unknown error")
                e.printStackTrace()
            }
        }
    }

    private suspend fun isVisited(url: String): Boolean {
        sharedMutex.withLock { return urlCache.contains(URI(url).toString()) }
    }

    private fun isValidUrl(url: String): Boolean {
        return !url.startsWith(ROOT_PAGE)
    }
}

fun main() {

    val properties = Properties()
    val log = LoggerFactory.getLogger("BookScraper")

    try {
        val fileInputStream = FileInputStream("config.properties")
        properties.load(fileInputStream)
        fileInputStream.close()

    } catch (e: Exception) {
        log.error("Issues reading config.properties!")
        e.printStackTrace()
    }

    val maxConcurrentJobs = properties.getProperty("app.maxConcurrentJobs").toInt()
    val maxDepth = properties.getProperty("app.maxDepth").toInt()
    val pageUrl = properties.getProperty("app.rootUrl")
    val destinationDir = properties.getProperty("app.destinationDir")

    val totalSteps = 1200L //Approx this many pages on site
    val progressBar = ProgressBar("Progress", totalSteps)

    val startTime = System.currentTimeMillis()

    val dispatcher = Executors.newFixedThreadPool(maxConcurrentJobs).asCoroutineDispatcher()

    dispatcher.use {
        runBlocking(dispatcher) {
            PageScraper().scrape(dispatcher, pageUrl, maxDepth, destinationDir, progressBar)
        }
    }

    val endTime = System.currentTimeMillis()
    progressBar.close()

    val elapsedTime = endTime - startTime
    val elapsedMinutes = elapsedTime / 60000L
    log.info("Time elapsed: $elapsedMinutes minutes")
}

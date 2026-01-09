package com.example.addonfinder

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL

data class SearchResult(val title: String, val pageUrl: String, val directUrl: String?)

class MainActivity : AppCompatActivity() {

    private lateinit var resultsList: RecyclerView
    private val results = mutableListOf<SearchResult>()
    private lateinit var adapter: ResultsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val versionInput = findViewById<TextView>(R.id.versionInput)
        val typeInput = findViewById<TextView>(R.id.typeInput)
        val searchBtn = findViewById<Button>(R.id.searchBtn)
        resultsList = findViewById(R.id.resultsList)

        adapter = ResultsAdapter(results) { result ->
            // direct download via DownloadManager if directUrl available, else open page
            val url = result.directUrl ?: result.pageUrl
            val request = DownloadManager.Request(Uri.parse(url))
            request.setTitle(result.title)
            request.setDescription("Downloading from AddonFinder")
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, Uri.parse(url).lastPathSegment)
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        }

        resultsList.layoutManager = LinearLayoutManager(this)
        resultsList.adapter = adapter

        searchBtn.setOnClickListener {
            val version = versionInput.text.toString().trim()
            val type = typeInput.text.toString().trim()
            val queryParts = mutableListOf<String>()
            if (type.isNotEmpty()) queryParts.add(type)
            if (version.isNotEmpty()) queryParts.add(version)
            val query = queryParts.joinToString(" ")
            performSearch(query)
        }
    }

    private fun performSearch(query: String) {
        results.clear()
        adapter.notifyDataSetChanged()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // fetch from MCPEDL search
                val mcpedl = fetchMcpedl(query)
                results.addAll(mcpedl)
                // fetch from CurseForge search
                val curse = fetchCurseForge(query)
                results.addAll(curse)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            withContext(Dispatchers.Main) {
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun fetchMcpedl(query: String): List<SearchResult> {
        val list = mutableListOf<SearchResult>()
        if (query.isEmpty()) return list
        val searchUrl = "https://mcpedl.com/?s=" + java.net.URLEncoder.encode(query, "UTF-8")
        val doc: Document = Jsoup.connect(searchUrl).userAgent("Mozilla/5.0").get()
        val articles = doc.select("article") // articles contain results
        for (a in articles) {
            val link = a.selectFirst("a[href]")?.attr("href") ?: continue
            val title = a.selectFirst("h2")?.text() ?: link
            // try to extract direct link by visiting page
            val direct = extractDirectFromPage(link)
            list.add(SearchResult(title, link, direct))
            if (list.size >= 10) break
        }
        return list
    }

    private fun fetchCurseForge(query: String): List<SearchResult> {
        val list = mutableListOf<SearchResult>()
        if (query.isEmpty()) return list
        val searchUrl = "https://www.curseforge.com/minecraft/addons/search?search=" + java.net.URLEncoder.encode(query, "UTF-8")
        val doc = Jsoup.connect(searchUrl).userAgent("Mozilla/5.0").get()
        val cards = doc.select("a.project-title")
        for (c in cards) {
            val link = "https://www.curseforge.com" + c.attr("href")
            val title = c.text()
            // On project page, find latest file download link (may require additional clicks)
            val direct = extractDirectFromCurseProject(link)
            list.add(SearchResult(title, link, direct))
            if (list.size >= 10) break
        }
        return list
    }

    private fun extractDirectFromPage(pageUrl: String): String? {
        try {
            val doc = Jsoup.connect(pageUrl).userAgent("Mozilla/5.0").get()
            // common hosts: mediafire, dropbox, github, drive.google, curseforge download links, direct .mcpack
            val selectors = listOf("a[href]")
            val links = doc.select(selectors.joinToString(","))
            for (el in links) {
                val href = el.attr("href")
                if (href.contains("mediafire.com") || href.contains("dropbox.com") || href.contains("github.com") || href.endsWith(".mcpack") || href.endsWith(".mcaddon") || href.endsWith(".mcworld") || href.endsWith(".zip") || href.endsWith(".apk")) {
                    // convert Google drive share to direct export if possible
                    if (href.contains("drive.google.com") && href.contains("/file/d/")) {
                        val id = href.split("/file/d/").getOrNull(1)?.split('/')[0]
                        if (id != null) return "https://drive.google.com/uc?export=download&id=$id"
                    }
                    return href
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun extractDirectFromCurseProject(projectUrl: String): String? {
        try {
            val doc = Jsoup.connect(projectUrl).userAgent("Mozilla/5.0").get()
            // Find file links - CurseForge loads files dynamically, but many project pages include link patterns to /files/...
            val fileLink = doc.select("a[href*=/files/]").firstOrNull()?.attr("href")
            if (fileLink != null) {
                val full = if (fileLink.startsWith("http")) fileLink else "https://www.curseforge.com" + fileLink
                // attempt to open the file page and find direct download button
                val fileDoc = Jsoup.connect(full).userAgent("Mozilla/5.0").get()
                val dl = fileDoc.select("a[href*=/download]").firstOrNull()?.attr("href")
                if (dl != null) {
                    return if (dl.startsWith("http")) dl else "https://www.curseforge.com" + dl
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    class ResultsAdapter(private val items: List<SearchResult>, private val onDownload: (SearchResult) -> Unit) : RecyclerView.Adapter<ResultsAdapter.VH>() {
        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.title)
            val link: TextView = view.findViewById(R.id.link)
            val btn: Button = view.findViewById(R.id.downloadBtn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_result, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.link.text = item.directUrl ?: item.pageUrl
            holder.btn.setOnClickListener { onDownload(item) }
        }

        override fun getItemCount(): Int = items.size
    }

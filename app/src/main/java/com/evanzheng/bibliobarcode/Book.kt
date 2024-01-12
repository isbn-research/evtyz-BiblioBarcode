package com.evanzheng.bibliobarcode

import android.text.Spanned
import androidx.core.text.HtmlCompat
import androidx.room.*
import org.json.JSONException
import org.json.JSONObject
import java.util.*

// We use a type converter because authors cannot be stored in an sql database without being converted into strings
@Entity(tableName = "books")
@TypeConverters(AuthorConverter::class)
class Book : Comparable<Book> {
    //ISBNs must be unique.
    @JvmField
    @PrimaryKey
    val isbn: String

    //Fields in SQL database
    @JvmField
    @ColumnInfo(name = "title")
    var title: String = ""

    @JvmField
    @ColumnInfo(name = "authors")
    var authors: MutableList<Author>

    @JvmField
    @ColumnInfo(name = "publisher")
    var publisher: String = ""

    @JvmField
    @ColumnInfo(name = "year")
    var year: String = ""

    @JvmField
    @ColumnInfo(name = "city")
    var city: String

    @JvmField
    @ColumnInfo(name = "state")
    var state: String

    //Citations (which we can build on the spot, for space efficiency)
    @Ignore
    var citation: String = ""

    @Ignore
    var rawFormatCitation: String = ""

    //When we call a book from the database, this is what is used to build it.
    constructor(
        isbn: String,
        title: String,
        authors: MutableList<Author>,
        publisher: String,
        year: String,
        city: String,
        state: String
    ) {
        this.isbn = isbn
        this.title = title
        this.authors = authors
        this.publisher = publisher
        this.year = year
        this.city = city
        this.state = state
    }

    //Constructor for book when we get a JSON file to parse.
    @Ignore
    internal constructor(info: JSONObject, isbn: String) {
        this.isbn = isbn
        title = try {
            info.getString("title")
        } catch (e: JSONException) {
            ""
        }
        authors = ArrayList<Author>()
        try {
            val rawAuthors = info.getJSONArray("authors")
            val numAuthors = rawAuthors.length()
            for (i in 0 until numAuthors) {
                val name = rawAuthors.getString(i)
                authors.add(Author(name))
            }
            //Authors must be in alphabetical order
            Collections.sort(authors)
        } catch (ignored: JSONException) {
        }
        publisher = try {
            info.getString("publisher")
        } catch (e: JSONException) {
            ""
        }
        try {
            year = info.getString("publishedDate")
            year = year.substring(0, Math.min(year.length, 4))
        } catch (e: JSONException) {
            year = ""
        }
        city = ""
        state = ""
    }

    //Creating an empty book with fake ISBN
    internal constructor(code: String) {
        isbn = code
        title = ""
        authors = ArrayList()
        publisher = ""
        year = ""
        city = ""
        state = ""
    }

    //Conversion between hashmap and book
    fun infoMapToList(bookInfo: Map<String?, String?>) {
        title = bookInfo["title"].toString()
        publisher = bookInfo["publisher"].toString()
        year = bookInfo["year"].toString()
        city = bookInfo["city"].toString()
        state = bookInfo["state"].toString()
    }

    //Converts hashmap to author list and string
    fun authorMapToList(authorMap: MutableMap<Int, Author?>) {
        authors = ArrayList<Author>()
        for (i in authorMap.keys) {
            authors.add(authorMap[i]!!)
        }
        Collections.sort(authors)
    }

    //Cites the book based on a style
    fun cite(style: String?) {
        //HTML citation
        rawFormatCitation = ""
        //Plain text citation
        citation = ""
        var authorCite: String
        val titleCite: String
        val publisherCite: String
        var yearCite: String
        val cityCite: String
        val stateCite: String
        when (style) {
            "MLA" -> {
                authorCite = ""
                if (authors.size != 0 && authors[0]!!.isNotEmpty) {
                    authorCite = authorCite + authors[0]!!.formattedName()
                    var i = 1
                    while (i < authors.size) {
                        authorCite = if (i == authors.size - 1) {
                            "$authorCite, and "
                        } else {
                            "$authorCite, "
                        }
                        authorCite = authorCite + authors[i]!!.fullName()
                        i++
                    }
                    authorCite = "$authorCite. "
                }
                titleCite = "$title. "
                publisherCite = if (publisher == "") {
                    "n.p., "
                } else {
                    "$publisher, "
                }
                yearCite = if (year == "") {
                    "n.d."
                } else {
                    "$year."
                }
                citation = authorCite + titleCite + publisherCite + yearCite
                rawFormatCitation = authorCite + "<i>" + titleCite + "</i>" + publisherCite + yearCite
            }
            "APA" -> {
                authorCite = ""
                var i = 0
                while (i < authors.size) {
                    authorCite = authorCite + authors[i]!!.formattedInitializedName()
                    if (i != authors.size - 1) {
                        authorCite = "$authorCite, "
                    }
                    if (i == authors.size - 2) {
                        authorCite = "$authorCite & "
                    }
                    i++
                }
                yearCite = " ("
                yearCite = if (year == "") {
                    yearCite + "n.d."
                } else {
                    yearCite + year
                }
                yearCite = "$yearCite). "
                titleCite = "$title. "
                if (city == "" || state == "") {
                    cityCite = "N.p.: "
                    stateCite = ""
                } else {
                    cityCite = "$city, "
                    stateCite = "$state: "
                }
                publisherCite = if (publisher == "") {
                    "n.p."
                } else {
                    "$publisher."
                }
                rawFormatCitation = authorCite + yearCite + "<i>" + titleCite + "</i>" + cityCite + stateCite + publisherCite
                citation = authorCite + yearCite + titleCite + cityCite + stateCite + publisherCite
            }
            "Chicago" -> {
                authorCite = ""
                if (authors.size != 0 && authors[0]!!.isNotEmpty) {
                    authorCite = authorCite + authors[0]!!.formattedName()
                    var i = 1
                    while (i < authors.size) {
                        authorCite = if (i == authors.size - 1) {
                            "$authorCite, and "
                        } else {
                            "$authorCite, "
                        }
                        authorCite = authorCite + authors[i]!!.fullName()
                        i++
                    }
                    authorCite = "$authorCite. "
                }
                titleCite = "$title. "
                cityCite = if (city == "") {
                    "N.p.: "
                } else {
                    "$city: "
                }
                publisherCite = if (publisher == "") {
                    "n.p., "
                } else {
                    "$publisher, "
                }
                yearCite = if (year == "") {
                    "n.d."
                } else {
                    "$year."
                }
                rawFormatCitation = authorCite + "<i>" + titleCite + "</i>" + cityCite + publisherCite + yearCite
                citation = authorCite + titleCite + cityCite + publisherCite + yearCite
            }
            "Harvard" -> {
                authorCite = ""
                if (authors.size != 0 && authors[0]!!.isNotEmpty) {
                    var i = 0
                    while (i < authors.size) {
                        authorCite = authorCite + authors[i]!!.formattedInitializedName() + ", "
                        if (i == authors.size - 2) {
                            authorCite = "$authorCite and "
                        }
                        i++
                    }
                }
                yearCite = if (year == "") {
                    "n.d. "
                } else {
                    "$year. "
                }
                titleCite = "$title. "
                cityCite = if (city == "") {
                    "N.p.: "
                } else {
                    "$city: "
                }
                publisherCite = if (publisher == "") {
                    "n.p."
                } else {
                    "$publisher."
                }
                rawFormatCitation = authorCite + yearCite + "<i>" + titleCite + "</i>" + cityCite + publisherCite
                citation = authorCite + yearCite + titleCite + cityCite + publisherCite
            }
            else -> rawFormatCitation = "$title Citation error"
        }
    }

    //Gets the citation of the current style, in HTML form.
    fun getCitation(): Spanned {
        return HtmlCompat.fromHtml(rawFormatCitation!!, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    //Implements comparable interface, where we compare books by alphabetical order of citation (to sort the bibliography)
    override fun compareTo(book: Book): Int {
        return citation!!.compareTo(book.citation!!)
    }
}
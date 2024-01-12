package com.evanzheng.bibliobarcode

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.evanzheng.bibliobarcode.BibliographyAdapter.BibliographyViewHolder
import java.util.*

class BibliographyAdapter  //Constructor method
internal constructor(// Style of citation
    var style: String, //Clipboard manager for copying individual citations
    private val clipboard: ClipboardManager
) : RecyclerView.Adapter<BibliographyViewHolder>() {
    //All books in bibliography
    var books = MainActivity.database?.bookDao()?.loadBookSources()

    //Creating a member of the list
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BibliographyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.citation, parent, false)
        return BibliographyViewHolder(view, clipboard)
    }

    //Binding it and linking it to a book
    override fun onBindViewHolder(holder: BibliographyViewHolder, position: Int) {
        val current = books?.get(position)
        holder.containerView.tag = current
        holder.citationView.text = current?.getCitation()
    }

    //Num items in list
    override fun getItemCount(): Int {
        return books!!.size
    }

    //Delete an item
    fun deleteItem(position: Int) {
        val current = books?.get(position)
        if (current != null) {
            MainActivity.database?.bookDao()?.deleteBook(current.isbn)
        }
        reload()
    }

    //Reload the list
    fun reload() {
        books = MainActivity.database?.bookDao()?.loadBookSources()
        for (book in books!!) {
            if (book != null) {
                book.cite(style)
            }
        }

        //Collections.sort(books)
        notifyDataSetChanged()
    }

    //The actual viewholder for each entry in recyclerview
    class BibliographyViewHolder(view: View, clipboard: ClipboardManager) :
        RecyclerView.ViewHolder(view) {
        val containerView: LinearLayout
        val citationView: TextView

        init {
            containerView = view.findViewById(R.id.citation_row)
            citationView = view.findViewById(R.id.citation)

            //Listen for touch to go to edit screen
            containerView.setOnClickListener { v: View ->
                val context = v.context
                val book = containerView.tag as Book
                val intent = Intent(context, BookActivity::class.java)
                intent.putExtra("isbn", book.isbn)
                context.startActivity(intent)
            }

            //Listen for long touch to copy text
            containerView.setOnLongClickListener { v: View ->
                val book = containerView.tag as Book
                val clip =
                    ClipData.newHtmlText("Bibliography", book.citation, book.rawFormatCitation)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(v.context, "Citation copied to clipboard!", Toast.LENGTH_SHORT)
                    .show()
                true
            }
        }
    }
}
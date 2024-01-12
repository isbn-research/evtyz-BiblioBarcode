package com.evanzheng.bibliobarcode

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONException
import org.json.JSONObject
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence
import uk.co.deanwild.materialshowcaseview.ShowcaseConfig
import java.util.*

class BookActivity : AppCompatActivity() {
    //Initialize Volley Request Queue
        private var requestQueue: RequestQueue? = null
    private var sharedPref: SharedPreferences? = null

    //Initialize book
    private var book: Book? = null

    //Initialize the maps that we will use to edit the book
    private var authors: MutableMap<Int, Author?>? = null
    private var bookInfo: MutableMap<String?, String?>? = null

    //Making new authors with unique ids so that editing remains consistent
    private var nextAuthorId = 0

    //Is this book new or was it already in the bibliography?
    private var isNew = false

    //Initialize views
    private var layoutInflater: LayoutInflater? = null
    private var viewGroup: ViewGroup? = null
    private var searchButton: Button? = null
    private var fieldAuthorEdit: ViewGroup? = null
    private var saveButton: FloatingActionButton? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book)

        //Set up views
        saveButton = findViewById(R.id.addToBibliography)
        saveButton?.setOnClickListener(View.OnClickListener { v: View? -> addToBibliography() })

        //Initialize toolbar and views
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        title = "Edit Info:"
        setSupportActionBar(toolbar)
        Objects.requireNonNull(supportActionBar)?.setDisplayShowHomeEnabled(true)
        Objects.requireNonNull(supportActionBar)?.setDisplayHomeAsUpEnabled(true)
        viewGroup = findViewById(R.id.listFields)
        searchButton = findViewById(R.id.searchLocation)
        searchButton?.setVisibility(View.INVISIBLE)

        //Set up layout inflater
        layoutInflater = getLayoutInflater()

        // Set up request queue
        requestQueue = Volley.newRequestQueue(applicationContext)
        val intent = intent

        //How did we enter this activity?
        var code = intent.getStringExtra("barcode")
        if (code == null) {
            code = intent.getStringExtra("isbn")
            if (code == null) {
                // We entered it via generating an empty book
                code = intent.getStringExtra("empty")
                isNew = true
                assert(code != null)
                book = Book(code!!)
                processAuthors()
                processBook()
            } else {
                // We entered it via editing an existing book
                book = MainActivity.database?.bookDao()?.loadBook(code)
                isNew = false
                saveButton?.setImageResource(R.drawable.content_save)
                processAuthors()
                processBook()
            }
        } else {
            // We entered it via scanning or entering an ISBN
            isNew = true
            loadBook(code)
        }


        //Run tutorial if necessary
        sharedPref = getPreferences(MODE_PRIVATE)
        val ranBefore = sharedPref?.getBoolean("edit", false)
        if (!ranBefore!!) {
            runTutorial()
        }
    }

    //Runs tutorial
    private fun runTutorial() {
        val tutorialConfig = ShowcaseConfig()
        tutorialConfig.delay = 500
        val placeholder = findViewById<LinearLayout>(R.id.placeholder)
        val tutorial = MaterialShowcaseSequence(this, "smthdif2")
        tutorial.setConfig(tutorialConfig)
        tutorial.addSequenceItem(placeholder, "Edit your book information here", "OKAY")
        tutorial.addSequenceItem(saveButton, "Save your book by pressing this button", "OKAY")
        tutorial.start()
        sharedPref!!.edit().putBoolean("edit", true).apply()
    }

    //On pressing the back button, go back
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return false
    }

    //Loads a book based on the code
    private fun loadBook(isbn: String) {
        //First API call
        val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:"+isbn
        val request = JsonObjectRequest(Request.Method.GET, url, null, { response: JSONObject ->
            try {
                //Gets the first item searched (book that corresponds to ISBN) and then gets the url of the selfLink
                val specificUrl = response
                    .getJSONArray("items")
                    .getJSONObject(0).getString("selfLink")

                //Second API call
                val specificRequest = JsonObjectRequest(
                    Request.Method.GET,
                    specificUrl,
                    null,
                    { response1: JSONObject ->
                        try {
                            //Gets info from API
                            val info = response1
                                .getJSONObject("volumeInfo")
                            //Generates a book based on the info
                            book = Book(info, isbn)
                            processAuthors()
                            processBook()
                        } catch (e2: JSONException) {
                            Toast.makeText(
                                this,
                                "We couldn't find this book. Error Code 2A",
                                Toast.LENGTH_LONG
                            ).show()
                            returnToCamera()
                        }
                    }) { error1: VolleyError? ->
                    Toast.makeText(
                        this,
                        "We couldn't find this book. Error Code 2B",
                        Toast.LENGTH_LONG
                    ).show()
                    returnToCamera()
                }
                requestQueue!!.add(specificRequest)
            } catch (e: JSONException) {
                Toast.makeText(this, "We couldn't find this book. Error Code 1A", Toast.LENGTH_LONG)
                    .show()
                returnToCamera()
            }
        }) { error: VolleyError? ->
            Toast.makeText(this, "We couldn't find this book. Error Code 1B", Toast.LENGTH_SHORT)
                .show()
            returnToCamera()
        }
        requestQueue!!.add(request)
    }

    //Convert book authors into hashmap
    private fun processAuthors() {
        authors = HashMap()
        if (book!!.authors.size != 0) {
            for (i in book!!.authors.indices) {
                (authors as HashMap<Int, Author?>)[i] = book!!.authors[i]
            }
        }
    }

    //Display the book's contents
    private fun processBook() {
        //Convert book info into hashmap
        bookInfo = HashMap()
        (bookInfo as HashMap<String?, String?>)["state"] = book!!.state
        (bookInfo as HashMap<String?, String?>)["city"] = book!!.city
        (bookInfo as HashMap<String?, String?>)["year"] = book!!.year
        (bookInfo as HashMap<String?, String?>)["publisher"] = book!!.publisher
        (bookInfo as HashMap<String?, String?>)["title"] = book!!.title

        //Show editing fields on screen
        showField("state", "State of Publication:", "State")
        showField("city", "City of Publication:", "City")
        showField("year", "Year of Publication:", "Year")
        showField("publisher", "Publisher:", "Publisher")
        showAuthors()
        showField("title", "Title:", "Title")
        searchButton!!.visibility = View.VISIBLE
    }

    //Showing field
    private fun showField(key: String, label: String, hint: String) {

        //Inflate the field
        @SuppressLint("InflateParams") val fieldLayout =
            layoutInflater!!.inflate(R.layout.field, null)

        //Set labels
        val description = fieldLayout.findViewById<TextView>(R.id.fieldDesc)
        description.text = label

        //Load data and hints into edittexts
        val titleEdit = fieldLayout.findViewById<EditText>(R.id.fieldEdit)
        titleEdit.setText(bookInfo!![key])
        titleEdit.hint = hint

        //Set a listener to edit the corresponding bookinfo entry when text is changed
        titleEdit.addTextChangedListener(object : BookTextWatcher(key) {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                bookInfo!![key] = s.toString()
            }
        })
        viewGroup!!.addView(
            fieldLayout,
            0,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    //Shows the author fields
    private fun showAuthors() {
        @SuppressLint("InflateParams") val authorLayout =
            layoutInflater!!.inflate(R.layout.field_author, null)
        val description = authorLayout.findViewById<TextView>(R.id.fieldDesc)
        description.setText(R.string.authordesc)
        fieldAuthorEdit = authorLayout.findViewById(R.id.fieldAuthorEdit)
        viewGroup!!.addView(
            authorLayout,
            0,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        nextAuthorId = book!!.authors.size

        //Based on the number of authors:
        for (i in 0 until nextAuthorId) {
            val first = book!!.authors[i]!!.first
            val middle = book!!.authors[i]!!.middle
            val last = book!!.authors[i]!!.last
            @SuppressLint("InflateParams") val authorAdd =
                layoutInflater!!.inflate(R.layout.author_add, null)
            val firstName = authorAdd.findViewById<EditText>(R.id.add_first)
            firstName.setText(first)
            firstName.addTextChangedListener(object : AuthorTextWatcher(i) {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    //Objects.requireNonNull(authors!!.get(id))?.first ?: = s.toString()
                }
            })
            val middleName = authorAdd.findViewById<EditText>(R.id.add_middle)
            middleName.setText(middle)
            middleName.addTextChangedListener(object : AuthorTextWatcher(i) {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    //Objects.requireNonNull(authors!![id])?.middle ?: = s . toString ()
                }
            })
            val lastName = authorAdd.findViewById<EditText>(R.id.add_last)
            lastName.setText(last)
            lastName.addTextChangedListener(object : AuthorTextWatcher(i) {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    //Objects.requireNonNull(authors!![id]).last = s.toString()
                }
            })
            fieldAuthorEdit?.addView(
                authorAdd,
                0,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            //A delete button to remove an author
            val deleteButton = authorAdd.findViewById<TextView>(R.id.deleteButton)
            deleteButton.setOnClickListener(object : AuthorDeleteListener(i) {
                override fun onClick(v: View) {
                    authors!!.remove(id)
                    fieldAuthorEdit?.removeView(authorAdd)
                }
            })
        }
    }

    //Search for location of publisher
    fun searchLocation(view: View?) {
        if (bookInfo!!["publisher"] == "") {
            return
        }
        val intent = Intent(Intent.ACTION_WEB_SEARCH)
        val keyword = Objects.requireNonNull(bookInfo!!["publisher"]) + " publisher location"
        intent.putExtra(SearchManager.QUERY, keyword)
        startActivity(intent)
    }

    //Add an author
    fun addAuthor(v: View?) {
        @SuppressLint("InflateParams") val authorAdd =
            layoutInflater!!.inflate(R.layout.author_add, null)
        val newAuthor = Author()

        //Add to hashmap
        authors!![nextAuthorId] = newAuthor
        val firstName = authorAdd.findViewById<EditText>(R.id.add_first)
        firstName.addTextChangedListener(object : AuthorTextWatcher(nextAuthorId) {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
               Objects.requireNonNull(authors!![id])?.first  = s.toString()
            }
        })
        val middleName = authorAdd.findViewById<EditText>(R.id.add_middle)
        middleName.addTextChangedListener(object : AuthorTextWatcher(nextAuthorId) {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                Objects.requireNonNull(authors!![id])?.middle = s.toString()
            }
        })
        val lastName = authorAdd.findViewById<EditText>(R.id.add_last)
        lastName.addTextChangedListener(object : AuthorTextWatcher(nextAuthorId) {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                Objects.requireNonNull(authors!![id])?.last = s.toString()
            }
        })
        fieldAuthorEdit!!.addView(
            authorAdd,
            fieldAuthorEdit!!.childCount,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        val deleteButton = authorAdd.findViewById<TextView>(R.id.deleteButton)
        deleteButton.setOnClickListener(object : AuthorDeleteListener(nextAuthorId) {
            override fun onClick(v: View) {
                authors!!.remove(id)
                fieldAuthorEdit!!.removeView(authorAdd)
            }
        })

        //Make sure ids are unique
        nextAuthorId++
    }

    //Add to bibliography function
    private fun addToBibliography() {

        //All books must have titles to properly cite
        if (bookInfo!!["title"] == "") {
            Toast.makeText(this, "Not possible to cite: please add a title!", Toast.LENGTH_LONG)
                .show()
            return
        }


        //Converts hashmap back to authorlist
        book!!.authorMapToList(authors!!)

        //Converts hashmap to book info
        book!!.infoMapToList(bookInfo!!)

        //Inserts or updates a book
        if (isNew) {
            MainActivity.database?.bookDao()?.insertBook(book!!)
        } else {
            MainActivity.database?.bookDao()?.updateBook(book!!)
        }

        //Goes to next activity
        val leaveIntent = Intent(this, BibliographyActivity::class.java)
        startActivity(leaveIntent)
    }

    //Returns to camera activity
    private fun returnToCamera() {
        val returnIntent = Intent(this, MainActivity::class.java)
        startActivity(returnIntent)
    }
}
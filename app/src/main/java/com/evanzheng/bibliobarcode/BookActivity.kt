package com.evanzheng.bibliobarcode;


import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence;
import uk.co.deanwild.materialshowcaseview.ShowcaseConfig;

public class BookActivity extends AppCompatActivity {

    //Initialize Volley Request Queue
    private RequestQueue requestQueue;

    private SharedPreferences sharedPref;


    //Initialize book
    private Book book;

    //Initialize the maps that we will use to edit the book
    private Map<Integer, Author> authors;
    private Map<String, String> bookInfo;

    //Making new authors with unique ids so that editing remains consistent
    private int nextAuthorId;

    //Is this book new or was it already in the bibliography?
    private boolean isNew;

    //Initialize views
    private LayoutInflater layoutInflater;
    private ViewGroup viewGroup;
    private Button searchButton;
    private ViewGroup fieldAuthorEdit;
    private FloatingActionButton saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book);

        //Set up views
        saveButton = findViewById(R.id.addToBibliography);
        saveButton.setOnClickListener(v -> addToBibliography());

        //Initialize toolbar and views
        Toolbar toolbar = findViewById(R.id.toolbar);
        setTitle("Edit Info:");
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowHomeEnabled(true);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        viewGroup = findViewById(R.id.listFields);
        searchButton = findViewById(R.id.searchLocation);
        searchButton.setVisibility(View.INVISIBLE);

        //Set up layout inflater
        layoutInflater = getLayoutInflater();

        // Set up request queue
        requestQueue = Volley.newRequestQueue(getApplicationContext());

        Intent intent = getIntent();

        //How did we enter this activity?
        String code = intent.getStringExtra("barcode");
        if (code == null) {
            code = intent.getStringExtra("isbn");
            if (code == null) {
                // We entered it via generating an empty book
                code = intent.getStringExtra("empty");
                isNew = true;
                assert code != null;
                book = new Book(code);
                processAuthors();
                processBook();
            } else {
                // We entered it via editing an existing book
                book = MainActivity.database.bookDao().loadBook(code);
                isNew = false;
                saveButton.setImageResource(R.drawable.content_save);
                processAuthors();
                processBook();
            }
        } else {
            // We entered it via scanning or entering an ISBN
            isNew = true;
            loadBook(code);
        }


        //Run tutorial if necessary
        sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        boolean ranBefore = sharedPref.getBoolean("edit", false);

        if (!ranBefore) {
            runTutorial();
        }

    }

    //Runs tutorial
    private void runTutorial() {
        ShowcaseConfig tutorialConfig = new ShowcaseConfig();
        tutorialConfig.setDelay(500);

        LinearLayout placeholder = findViewById(R.id.placeholder);

        MaterialShowcaseSequence tutorial = new MaterialShowcaseSequence(this, "smthdif2");

        tutorial.setConfig(tutorialConfig);
        tutorial.addSequenceItem(placeholder, "Edit your book information here", "OKAY");
        tutorial.addSequenceItem(saveButton, "Save your book by pressing this button", "OKAY");
        tutorial.start();

        sharedPref.edit().putBoolean("edit", true).apply();
    }

    //On pressing the back button, go back
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    //Loads a book based on the code
    private void loadBook(String isbn) {
        //First API call
        String url = "https://www.googleapis.com/books/v1/volumes?q=isbn:"
                .concat(isbn);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null, response -> {
            try {
                //Gets the first item searched (book that corresponds to ISBN) and then gets the url of the selfLink
                String specificUrl = response
                        .getJSONArray("items")
                        .getJSONObject(0).getString("selfLink");

                //Second API call
                JsonObjectRequest specificRequest = new JsonObjectRequest(Request.Method.GET, specificUrl, null, response1 -> {
                    try {
                        //Gets info from API
                        JSONObject info = response1
                                .getJSONObject("volumeInfo");
                        //Generates a book based on the info
                        book = new Book(info, isbn);
                        processAuthors();
                        processBook();
                    } catch (JSONException e2) {
                        Toast.makeText(this, "We couldn't find this book. Error Code 2A", Toast.LENGTH_LONG).show();
                        returnToCamera();
                    }
                }, error1 ->
                {
                    Toast.makeText(this, "We couldn't find this book. Error Code 2B", Toast.LENGTH_LONG).show();
                    returnToCamera();
                });

                requestQueue.add(specificRequest);

            } catch (JSONException e) {
                Toast.makeText(this, "We couldn't find this book. Error Code 1A", Toast.LENGTH_LONG).show();
                returnToCamera();
            }
        }, error ->
        {
            Toast.makeText(this, "We couldn't find this book. Error Code 1B", Toast.LENGTH_SHORT).show();
            returnToCamera();
        });

        requestQueue.add(request);
    }

    //Convert book authors into hashmap
    private void processAuthors() {
        authors = new HashMap<>();
        if (book.authors.size() != 0) {
            for (int i = 0; i < book.authors.size(); i++) {
                authors.put(i, book.authors.get(i));
            }
        }
    }

    //Display the book's contents
    private void processBook() {
        //Convert book info into hashmap
        bookInfo = new HashMap<>();
        bookInfo.put("state", book.state);
        bookInfo.put("city", book.city);
        bookInfo.put("year", book.year);
        bookInfo.put("publisher", book.publisher);
        bookInfo.put("title", book.title);

        //Show editing fields on screen
        showField("state", "State of Publication:", "State");
        showField("city", "City of Publication:", "City");
        showField("year", "Year of Publication:", "Year");
        showField("publisher", "Publisher:", "Publisher");
        showAuthors();
        showField("title", "Title:", "Title");
        searchButton.setVisibility(View.VISIBLE);
    }

    //Showing field
    private void showField(String key, String label, String hint) {

        //Inflate the field
        @SuppressLint("InflateParams") View fieldLayout = layoutInflater.inflate(R.layout.field, null);

        //Set labels
        TextView description = fieldLayout.findViewById(R.id.fieldDesc);
        description.setText(label);

        //Load data and hints into edittexts
        EditText titleEdit = fieldLayout.findViewById(R.id.fieldEdit);
        titleEdit.setText(bookInfo.get(key));
        titleEdit.setHint(hint);

        //Set a listener to edit the corresponding bookinfo entry when text is changed
        titleEdit.addTextChangedListener(new BookTextWatcher(key) {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                bookInfo.put(key, s.toString());
            }
        });
        viewGroup.addView(fieldLayout, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    //Shows the author fields
    private void showAuthors() {
        @SuppressLint("InflateParams") View authorLayout = layoutInflater.inflate(R.layout.field_author, null);
        TextView description = authorLayout.findViewById(R.id.fieldDesc);
        description.setText(R.string.authordesc);
        fieldAuthorEdit = authorLayout.findViewById(R.id.fieldAuthorEdit);

        viewGroup.addView(authorLayout, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        nextAuthorId = book.authors.size();

        //Based on the number of authors:
        for (int i = 0; i < nextAuthorId; i++) {

            String first = book.authors.get(i).first;
            String middle = book.authors.get(i).middle;
            String last = book.authors.get(i).last;

            @SuppressLint("InflateParams") View authorAdd = layoutInflater.inflate(R.layout.author_add, null);
            EditText firstName = authorAdd.findViewById(R.id.add_first);
            firstName.setText(first);
            firstName.addTextChangedListener(new AuthorTextWatcher(i) {
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    Objects.requireNonNull(authors.get(id)).first = s.toString();
                }
            });

            EditText middleName = authorAdd.findViewById(R.id.add_middle);
            middleName.setText(middle);
            middleName.addTextChangedListener(new AuthorTextWatcher(i) {
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    Objects.requireNonNull(authors.get(id)).middle = s.toString();
                }
            });


            EditText lastName = authorAdd.findViewById(R.id.add_last);
            lastName.setText(last);
            lastName.addTextChangedListener(new AuthorTextWatcher(i) {
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    Objects.requireNonNull(authors.get(id)).last = s.toString();
                }
            });
            fieldAuthorEdit.addView(authorAdd, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            //A delete button to remove an author
            TextView deleteButton = authorAdd.findViewById(R.id.deleteButton);
            deleteButton.setOnClickListener(new AuthorDeleteListener(i) {
                @Override
                public void onClick(View v) {
                    authors.remove(id);
                    fieldAuthorEdit.removeView(authorAdd);
                }
            });
        }
    }

    //Search for location of publisher
    @SuppressWarnings("unused")
    public void searchLocation(View view) {
        if (Objects.equals(bookInfo.get("publisher"), "")) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        String keyword = Objects.requireNonNull(bookInfo.get("publisher")).concat(" publisher location");
        intent.putExtra(SearchManager.QUERY, keyword);
        startActivity(intent);
    }

    //Add an author
    @SuppressWarnings("unused")
    public void addAuthor(View v) {
        @SuppressLint("InflateParams") View authorAdd = layoutInflater.inflate(R.layout.author_add, null);

        Author newAuthor = new Author();

        //Add to hashmap
        authors.put(nextAuthorId, newAuthor);

        EditText firstName = authorAdd.findViewById(R.id.add_first);
        firstName.addTextChangedListener(new AuthorTextWatcher(nextAuthorId) {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Objects.requireNonNull(authors.get(id)).first = s.toString();
            }
        });

        EditText middleName = authorAdd.findViewById(R.id.add_middle);
        middleName.addTextChangedListener(new AuthorTextWatcher(nextAuthorId) {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Objects.requireNonNull(authors.get(id)).middle = s.toString();
            }
        });


        EditText lastName = authorAdd.findViewById(R.id.add_last);
        lastName.addTextChangedListener(new AuthorTextWatcher(nextAuthorId) {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Objects.requireNonNull(authors.get(id)).last = s.toString();
            }
        });

        fieldAuthorEdit.addView(authorAdd, fieldAuthorEdit.getChildCount(), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView deleteButton = authorAdd.findViewById(R.id.deleteButton);
        deleteButton.setOnClickListener(new AuthorDeleteListener(nextAuthorId) {
            @Override
            public void onClick(View v) {
                authors.remove(id);
                fieldAuthorEdit.removeView(authorAdd);
            }
        });

        //Make sure ids are unique
        nextAuthorId++;

    }

    //Add to bibliography function
    private void addToBibliography() {

        //All books must have titles to properly cite
        if (Objects.equals(bookInfo.get("title"), "")) {
            Toast.makeText(this, "Not possible to cite: please add a title!", Toast.LENGTH_LONG).show();
            return;
        }


        //Converts hashmap back to authorlist
        book.authorMapToList(authors);

        //Converts hashmap to book info
        book.infoMapToList(bookInfo);

        //Inserts or updates a book
        if (isNew) {
            MainActivity.database.bookDao().insertBook(book);
        } else {
            MainActivity.database.bookDao().updateBook(book);
        }

        //Goes to next activity
        Intent leaveIntent = new Intent(this, BibliographyActivity.class);
        startActivity(leaveIntent);
    }

    //Returns to camera activity
    private void returnToCamera() {
        Intent returnIntent = new Intent(this, MainActivity.class);
        startActivity(returnIntent);
    }
}

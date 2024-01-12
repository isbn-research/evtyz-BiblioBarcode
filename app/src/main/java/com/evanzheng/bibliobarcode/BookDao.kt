package com.evanzheng.bibliobarcode

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

//A DAO that manages queries to our SQL database
@Dao
interface BookDao {
    //Inserting a book into the database
    @Insert
    fun insertBook(book: Book)

    //Updating an existing book in the database
    @Update
    fun updateBook(book: Book)

    //Deleting a book with a certain ISBN
    @Query("DELETE FROM books WHERE isbn=:isbn")
    fun deleteBook(isbn: String)

    //Getting list of all ISBNs currently in database
    @Query("SELECT isbn FROM books")
    fun loadISBN(): List<String>?

    //Getting list of all books currently in database
    @Query("SELECT * FROM books")
    fun loadBookSources(): List<Book>?

    //Selecting a book with a particular ISBN from database
    @Query("SELECT * FROM books WHERE isbn =:isbn")
    fun loadBook(isbn: String): Book?
}
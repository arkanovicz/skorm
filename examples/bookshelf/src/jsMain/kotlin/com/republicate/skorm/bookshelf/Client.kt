package com.republicate.skorm.bookshelf

import com.republicate.skorm.ApiClient
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

lateinit var exampleDatabase: ExampleDatabase<ApiClient>

fun reserve(bookId: Int) {
    console.log("reserve $bookId")
    GlobalScope.launch {
        exampleDatabase = ExampleDatabase(ApiClient("..."))
        ExampleDatabase.bookshelf.attempt("reserve", bookId)
    }
}

fun main() {
    window.onload = {
        console.log("loaded")
    }
}

package com.republicate.skorm.bookshelf

import com.republicate.skorm.ApiClient
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

val exampleDatabase = ExampleDatabase(ApiClient("${window.location.href}/api"))

fun reserve(bookId: Int) {
    console.log("reserve $bookId")
    GlobalScope.launch {
        ExampleDatabase.bookshelf.attempt("reserve", bookId)
    }
}

fun main() {
    window.onload = {
        console.log("loaded")
    }
}

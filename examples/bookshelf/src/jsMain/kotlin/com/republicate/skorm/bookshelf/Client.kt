package com.republicate.skorm.bookshelf

import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun reserve(bookId: Int) {
    console.log("reserve $bookId")
    GlobalScope.launch {
        exampleDatabase.bookshelf.attempt("reserve", bookId)
    }
}

fun main() {
    window.onload = {
        console.log("loaded")
    }
}

package com.republicate.skorm.bookshelf

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Document
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.EventTarget

typealias EventHandler = (Event) -> Unit

// sel

fun sel(selector: String): Collection<HTMLElement> {
    val elements = document.querySelectorAll(selector)
    return elements.asList().map { it as HTMLElement }.toList()
}

// on

fun EventTarget.on(event: String, handler: EventHandler) {
    addEventListener(event, handler)
}

fun Collection<HTMLElement>.on(event: String, handler: EventHandler) {
    forEach { it.on(event, handler) }
}

// target

fun Event.element() = target as HTMLElement

// click

fun EventTarget.click(handler: EventHandler) {
    on("click", handler)
}

fun Collection<HTMLElement>.click(handler: EventHandler) {
    on("click", handler)
}

// submit

fun EventTarget.submit(handler: EventHandler) {
    on("submit", handler)
}

fun Collection<HTMLElement>.submit(handler: EventHandler) {
    on("submit", handler)
}


// classes

fun HTMLElement.addClass(cls: String) {
    classList.add(cls)
}

fun HTMLElement.removeClass(cls: String) {
    classList.remove(cls)
}

fun HTMLElement.toggleClass(cls: String) {
    classList.toggle(cls)
}

fun Collection<HTMLElement>.addClass(cls: String) {
    forEach { it.addClass(cls) }
}

fun Collection<HTMLElement>.removeClass(cls: String) {
    forEach { it.removeClass(cls) }
}

fun Collection<HTMLElement>.toggleClass(cls: String) {
    forEach { it.toggleClass(cls) }
}

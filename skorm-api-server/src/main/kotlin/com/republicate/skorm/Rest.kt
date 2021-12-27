package com.republicate.skorm

import io.ktor.routing.Route

fun Route.rest(path: String, build: Route.() -> Unit): Route {
    return this
}

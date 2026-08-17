package com.dozycoffee.catalog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DozyCatalogApiApplication

fun main(args: Array<String>) {
    runApplication<DozyCatalogApiApplication>(*args)
}

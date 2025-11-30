package com.malec.awsm

import kotlin.random.Random

fun input(): Int {
    return Random.nextInt()
}

fun output(value: Int) {
    println("Output: $value")
}

infix fun Int.nor(b: Int): Int {
    return (this or b).inv()
}

infix fun Int.nand(b: Int): Int {
    return (this and b).inv()
}
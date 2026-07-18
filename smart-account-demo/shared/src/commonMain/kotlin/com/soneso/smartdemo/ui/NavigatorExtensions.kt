package com.soneso.smartdemo.ui

import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator

/**
 * Pushes [screen] unless a screen of the same type is already on top of the stack.
 *
 * Guards rapid repeated taps on navigation buttons: a double tap would otherwise push
 * duplicate instances of the same screen, whose identical transition keys make the
 * slide transition fail and corrupt the navigator.
 */
fun Navigator.pushOnce(screen: Screen) {
    if (lastItem::class != screen::class) {
        push(screen)
    }
}

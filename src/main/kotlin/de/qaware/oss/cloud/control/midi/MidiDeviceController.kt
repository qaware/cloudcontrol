/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 QAware GmbH, Munich, Germany
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package de.qaware.oss.cloud.control.midi

import de.qaware.oss.cloud.control.midi.LaunchControl.*

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.inject.Inject

/**
 * The controller takes care of reacting to MIDI device events properly.
 */
@ApplicationScoped
open class MidiDeviceController @Inject constructor(private val launchControl: LaunchControl) {

    @PostConstruct
    open fun initDevice() {
        launchControl.resetLEDs()

        launchControl.color(Channel.USER, Cursor.UP, Color.RED_LOW)
        launchControl.color(Channel.USER, Cursor.DOWN, Color.RED_LOW)
        launchControl.color(Channel.USER, Cursor.LEFT, Color.RED_LOW)
        launchControl.color(Channel.USER, Cursor.RIGHT, Color.RED_LOW)

        launchControl.color(Channel.FACTORY, Cursor.UP, Color.RED_LOW)
        launchControl.color(Channel.FACTORY, Cursor.DOWN, Color.RED_LOW)
        launchControl.color(Channel.FACTORY, Cursor.LEFT, Color.RED_LOW)
        launchControl.color(Channel.FACTORY, Cursor.RIGHT, Color.RED_LOW)
    }

    @PreDestroy
    open fun shutdownDevice() {
        launchControl.resetLEDs()
    }

    open fun onCursorPressed(@Observes @Pressed event: CursorEvent) {
        launchControl.color(event.channel, event.cursor, Color.RED_FULL)
    }

    open fun onCursorReleased(@Observes @Released event: CursorEvent) {
        launchControl.color(event.channel, event.cursor, Color.RED_LOW)
    }

    open fun onButtonPressed(@Observes @Pressed event: ButtonEvent) {
        // TODO do something with this event
    }

    open fun onButtonReleased(@Observes @Pressed event: ButtonEvent) {
        // TODO do something with this event
    }

    open fun onKnobTurned(@Observes event: KnobEvent) {
        if (event.value == 0) {
            launchControl.color(event.channel, Button.findByNumber(event.index)!!, Color.AMBER_FULL)
        } else {
            launchControl.color(event.channel, Button.findByNumber(event.index)!!, Color.GREEN_FULL)
        }
    }

    open fun off(index: Int) = color(index, Color.OFF)

    open fun enable(index: Int) = color(index, Color.GREEN_FULL)

    open fun disable(index: Int) = color(index, Color.AMBER_FULL)

    open fun failure(index: Int) = color(index, Color.RED_FULL)

    private fun color(index: Int, color: Color) {
        if (index !in 0..15) return

        val channel = Channel.findByIndex(index)
        val button = Button.findByIndex(index)
        launchControl.color(channel!!, button!!, color)
    }
}
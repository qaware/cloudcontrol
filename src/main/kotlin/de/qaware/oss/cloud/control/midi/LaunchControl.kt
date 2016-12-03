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

import org.slf4j.Logger
import javax.annotation.PostConstruct
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Event
import javax.enterprise.util.AnnotationLiteral
import javax.inject.Inject
import javax.sound.midi.MidiMessage
import javax.sound.midi.Receiver
import javax.sound.midi.ShortMessage
import javax.sound.midi.Transmitter

/**
 * This class represents the Novation Launch Control MIDI device. It encapsulates
 * all the device access specific logic to receive and send Java sound MIDI messages.
 */
@ApplicationScoped
open class LaunchControl @Inject constructor(private val transmitter: Transmitter,
                                             private val receiver: Receiver,
                                             private val cursorEvent: Event<CursorEvent>,
                                             private val logger: Logger) {

    /**
     * Register with the Launchpad to receive MIDI messages.
     */
    @PostConstruct
    open fun postConstruct() {
        transmitter.receiver = object : Receiver {
            override fun send(message: MidiMessage, timeStamp: Long) {
                if (message is ShortMessage) handle(message)
            }

            override fun close() {
            }
        }
    }

    private fun handle(message: ShortMessage) {
        logger.debug("Handling MIDI message [{} {} {} {}]",
                message.channel, message.command, message.data1, message.data2)

        when (message.data1) {
            in 114..117 -> {
                val cursor = Cursor.find(message.command, message.data1)
                cursorEvent.select(qualifier(message.data2)).fire(CursorEvent(message.channel, cursor))
            }
        }
    }

    private fun qualifier(data2: Int): Annotation {
        when (data2) {
            127 -> return object : AnnotationLiteral<Pressed>() {}
            else -> return object : AnnotationLiteral<Released>() {}
        }
    }

    open fun resetLEDs() {
        // reset all LEDs on both channels
        receiver.send(ShortMessage(176, 0, 0, 0), -1)
        receiver.send(ShortMessage(176, 8, 0, 0), -1)
    }

    open fun color(channel: Int, cursor: Cursor, color: Color) {
        receiver.send(ShortMessage(cursor.command, channel, cursor.value, color.value), -1)
    }

    /**
     * Enum class for the 4 cursor buttons.
     */
    enum class Cursor(val command: Int, val value: Int) {
        UP(176, 114), DOWN(176, 115), LEFT(176, 116), RIGHT(176, 117);

        companion object {
            /**
             * Finds the Cursor by the given command and value.
             *
             * @param command the command
             * @param value the value
             * @return the Cursor if found
             */
            fun find(command: Int, value: Int) = values().find { it.command == command && it.value == value }
        }
    }

    /**
     * Enum class for the 8 press buttons.
     */
    enum class Button(val command: Int, val value: Int) {
        BUTTON_1(144, 9),
        BUTTON_2(144, 10),
        BUTTON_3(144, 11),
        BUTTON_4(144, 12),
        BUTTON_5(144, 25),
        BUTTON_6(144, 26),
        BUTTON_7(144, 27),
        BUTTON_8(144, 28),
    }

    /**
     * Enum class for supported used colors.
     */
    enum class Color(val value: Int) {
        OFF(12),
        RED_LOW(13),
        RED_FULL(15),
        AMBER_LOW(29),
        AMBER_FULL(63),
        YELLOW(62),
        GREEN_LOW(28),
        GREEN_FULL(60)
    }
}
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
                                             private val buttonEvent: Event<ButtonEvent>,
                                             private val cursorEvent: Event<CursorEvent>,
                                             private val knobEvent: Event<KnobEvent>,
                                             private val logger: Logger) {

    /**
     * Register with the Launchpad to receive MIDI messages.
     */
    @PostConstruct
    open fun register() {
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

        if (message.command == 176) {
            // these are cursor or knob events
            when (message.data1) {
                in 21..28 -> {
                    knobEvent.fire(KnobEvent(Channel.find(message.channel)!!, 1, message.data1 - 20, message.data2))
                }

                in 41..48 -> {
                    knobEvent.fire(KnobEvent(Channel.find(message.channel)!!, 2, message.data1 - 40, message.data2))
                }

                in 114..117 -> {
                    val cursor = Cursor.find(message.command, message.data1)
                    cursorEvent.select(qualifier(message.data2)).fire(CursorEvent(Channel.find(message.channel)!!, cursor!!))
                }
            }
        } else {
            // these are button press or release commands
            when (message.data1) {
                in 9..12 -> {
                    val button = Button.find(message.command, message.data1)
                    buttonEvent.select(qualifier(message.data2)).fire(ButtonEvent(Channel.find(message.channel)!!, button!!))
                }

                in 25..28 -> {
                    val button = Button.find(message.command, message.data1)
                    buttonEvent.select(qualifier(message.data2)).fire(ButtonEvent(Channel.find(message.channel)!!, button!!))
                }
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

    open fun color(channel: Channel, switchable: Switchable, color: Color) {
        receiver.send(ShortMessage(switchable.command, channel.value, switchable.value, color.value), -1)
    }

    /**
     * Common interface for all switchable buttons on the device.
     */
    interface Switchable {
        val command: Int
        val value: Int
    }

    /**
     * Enum class for the 4 cursor buttons.
     */
    enum class Cursor(override val command: Int, override val value: Int) : Switchable {
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
    enum class Button(override val command: Int, override val value: Int) : Switchable {
        BUTTON_1(144, 9),
        BUTTON_2(144, 10),
        BUTTON_3(144, 11),
        BUTTON_4(144, 12),
        BUTTON_5(144, 25),
        BUTTON_6(144, 26),
        BUTTON_7(144, 27),
        BUTTON_8(144, 28);

        companion object {
            /**
             * Finds the Button by the given command and value.
             *
             * @param command the command
             * @param value the value
             * @return the Button if found
             */
            fun find(command: Int, value: Int) = Button.values().find { it.command == command && it.value == value }
        }
    }

    /**
     * Enum class for the two supported channels.
     */
    enum class Channel(val value: Int) {
        USER(0), FACTORY(8);

        companion object {
            /**
             * Finds the Channel by the given value.
             *
             * @param value the value
             * @return the Channel if found
             */
            fun find(value: Int) = Channel.values().find { it.value == value }
        }
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
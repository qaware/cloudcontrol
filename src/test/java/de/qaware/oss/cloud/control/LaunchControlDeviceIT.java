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
package de.qaware.oss.cloud.control;

import org.junit.Test;

import javax.sound.midi.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

/**
 * Integration test for the Novation Launch Control.
 */
public class LaunchControlDeviceIT {

    @Test
    public void testMidiDevicesForLaunchpadMK2() throws Exception {
        // get the list of all midi devices
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
        assertThat(infos, is(notNullValue()));
        assertThat(infos.length, is(greaterThan(0)));

        // filter out the list of launch pads
        List<MidiDevice.Info> launchpads = Arrays.stream(infos)
                .filter(i -> "Launch Control".equals(i.getName()))
                .collect(Collectors.toList());
        assertThat(launchpads, hasSize(2));
    }

    @Test
    public void testDefaultMidiSystemTransmitterReceiver() throws Exception {
        // these here do not seem to make any difference
        // maybe when there are multiple MIDI devices
        System.setProperty("javax.sound.midi.Transmitter", "com.sun.media.sound.MidiInDeviceProvider#Launch Control");
        System.setProperty("javax.sound.midi.Receiver", "com.sun.media.sound.MidiOutDeviceProvider#Launch Control");

        // check the default transmitter and receiver
        Transmitter transmitter = MidiSystem.getTransmitter();
        assertThat(transmitter, is(notNullValue()));
        transmitter.close();

        Receiver receiver = MidiSystem.getReceiver();
        assertThat(receiver, is(notNullValue()));
        receiver.close();
    }

    @Test
    public void testBasicInteraction() throws Exception {
        System.setProperty("javax.sound.midi.Transmitter", "com.sun.media.sound.MidiInDeviceProvider#Launchpad MK2");
        System.setProperty("javax.sound.midi.Receiver", "com.sun.media.sound.MidiOutDeviceProvider#Launchpad MK2");

        Receiver receiver = MidiSystem.getReceiver();

        // all LEDs off
        receiver.send(new ShortMessage(176, 8, 0, 0), -1);
        TimeUnit.SECONDS.sleep(1);

        // all LEDs low medium bright
        receiver.send(new ShortMessage(176, 8, 0, 125), -1);
        TimeUnit.SECONDS.sleep(1);
        receiver.send(new ShortMessage(176, 8, 0, 126), -1);
        TimeUnit.SECONDS.sleep(1);
        receiver.send(new ShortMessage(176, 8, 0, 127), -1);
        TimeUnit.SECONDS.sleep(1);

        // all LEDs off
        receiver.send(new ShortMessage(176, 8, 0, 0), -1);
        TimeUnit.SECONDS.sleep(1);

        // up / down / left / right LEDs in Red full
        receiver.send(new ShortMessage(176, 8, 114, 15), -1);
        TimeUnit.MILLISECONDS.sleep(500);
        receiver.send(new ShortMessage(176, 8, 115, 15), -1);
        TimeUnit.MILLISECONDS.sleep(500);
        receiver.send(new ShortMessage(176, 8, 116, 15), -1);
        TimeUnit.MILLISECONDS.sleep(500);
        receiver.send(new ShortMessage(176, 8, 117, 15), -1);
        TimeUnit.MILLISECONDS.sleep(500);

        // full brightness 1 2 3 4
        receiver.send(new ShortMessage(144, 8, 9, 15), -1);
        TimeUnit.MILLISECONDS.sleep(500);
        receiver.send(new ShortMessage(144, 8, 10, 63), -1);
        TimeUnit.MILLISECONDS.sleep(500);
        receiver.send(new ShortMessage(144, 8, 11, 62), -1);
        TimeUnit.MILLISECONDS.sleep(500);
        receiver.send(new ShortMessage(144, 8, 12, 60), -1);
        TimeUnit.MILLISECONDS.sleep(500);

        // flashing LEDs 5 6 7 8 ??
        receiver.send(new ShortMessage(144, 8, 25, 11), -1);
        TimeUnit.MILLISECONDS.sleep(500);
        receiver.send(new ShortMessage(144, 8, 26, 59), -1);
        TimeUnit.MILLISECONDS.sleep(500);
        receiver.send(new ShortMessage(144, 8, 27, 58), -1);
        TimeUnit.MILLISECONDS.sleep(500);
        receiver.send(new ShortMessage(144, 8, 28, 56), -1);
        TimeUnit.MILLISECONDS.sleep(500);

        Transmitter transmitter = MidiSystem.getTransmitter();
        transmitter.setReceiver(loggingReceiver());

        // System.in.read();
        receiver.send(new ShortMessage(176, 8, 0, 0), -1);
    }

    private Receiver loggingReceiver() {
        return new Receiver() {
            @Override
            public void send(MidiMessage message, long timeStamp) {
                if (!(message instanceof ShortMessage))
                    return;

                ShortMessage sm = (ShortMessage) message;
                System.out.format("Channel %d, Command %d, Data1 %d, Data2 %d%n",
                        sm.getChannel(), sm.getCommand(), sm.getData1(), sm.getData2());
            }

            @Override
            public void close() {
            }
        };
    }
}

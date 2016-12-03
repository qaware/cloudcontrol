@file:JvmName("CloudControlApplication")

package de.qaware.oss.cloud.control

import org.apache.deltaspike.cdise.api.CdiContainerLoader
import org.slf4j.bridge.SLF4JBridgeHandler
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.Dependent

/**
 * The Cloud Control main application class. Boots the CDI context and
 * initializes the MIDI environment and device properly.
 */
fun main(args: Array<String>) {
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()

    // this seems to be required at least under Windows
    System.setProperty("javax.sound.midi.Transmitter", "com.sun.media.sound.MidiInDeviceProvider#Launch Control")
    System.setProperty("javax.sound.midi.Receiver", "com.sun.media.sound.MidiOutDeviceProvider#Launch Control")

    // get cluster service property or initialize default
    var orchestrator = System.getProperty("cloud.orchestrator")
    if (orchestrator == null) {
        orchestrator = "kubernetes"
        System.setProperty("cluster.orchestrator", orchestrator)
    }

    // get the current CDI container
    val cdiContainer = CdiContainerLoader.getCdiContainer()
    cdiContainer.boot()

    // and initialize the CDI container control
    val contextControl = cdiContainer.contextControl;
    contextControl.startContext(Dependent::class.java)
    contextControl.startContext(ApplicationScoped::class.java)

    // ensure we shutdown nicely on exit
    Runtime.getRuntime().addShutdownHook(Thread() {
        cdiContainer.shutdown()
    })
}
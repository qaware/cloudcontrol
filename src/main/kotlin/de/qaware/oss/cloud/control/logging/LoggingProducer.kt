package de.qaware.oss.cloud.control.logging

import org.slf4j.LoggerFactory
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.context.Dependent
import javax.enterprise.inject.Produces
import javax.enterprise.inject.spi.InjectionPoint

/**
 * Simple CDI producer for SLF4J loggers.
 */
@ApplicationScoped
class LoggingProducer {
    @Produces
    @Dependent
    fun create(ij: InjectionPoint) = LoggerFactory.getLogger(ij.member.declaringClass)
}
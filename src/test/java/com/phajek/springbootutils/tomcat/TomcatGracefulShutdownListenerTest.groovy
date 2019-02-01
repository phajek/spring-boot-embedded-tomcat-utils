package com.phajek.springbootutils.tomcat

import org.apache.catalina.connector.Connector
import org.apache.catalina.startup.Tomcat
import org.apache.coyote.ProtocolHandler
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.event.ContextClosedEvent

import java.time.Duration
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import static org.mockito.Mockito.*

/**
 * Test of {@link TomcatGracefulShutdownListener}.
 * @author Petr Hajek
 */
class TomcatGracefulShutdownListenerTest {

    TomcatGracefulShutdownListener shutdownListener;
    Connector mockedConnector
    ProtocolHandler mockedProtocolHandler
    ServletWebServerApplicationContext mockedWebServerContext
    TomcatWebServer mockedTomcatServer
    Tomcat mockedTomcat

    private static final long GRACEFUL_TIMEOUT = 10
    private static final long FORCEFUL_TIMEOUT = 2

    @Before
    public void prepare() {
        shutdownListener = new TomcatGracefulShutdownListener(Duration.ofSeconds(GRACEFUL_TIMEOUT), Duration.ofSeconds(FORCEFUL_TIMEOUT))

        // Init mocks.
        mockedConnector = mock(Connector.class)
        mockedTomcatServer = mock(TomcatWebServer.class)
        mockedTomcat = mock(Tomcat.class)
        mockedProtocolHandler = mock(ProtocolHandler.class)
        mockedWebServerContext = mock(ServletWebServerApplicationContext.class)

        // Setup mocks.
        when(mockedConnector.getProtocolHandler()).thenReturn(mockedProtocolHandler)
        when(mockedTomcat.getConnector()).thenReturn(mockedConnector)
        when(mockedTomcatServer.getTomcat()).thenReturn(mockedTomcat)
        when(mockedWebServerContext.getWebServer()).thenReturn(mockedTomcatServer)

        // Fill listener.
        shutdownListener.webServerContext = mockedWebServerContext
    }

    @Test
    public void "successful graceful shutdown"() {
        ThreadPoolExecutor mockedExecutor = mock(ThreadPoolExecutor.class)
        when(mockedProtocolHandler.getExecutor()).thenReturn(mockedExecutor)

        when(mockedExecutor.awaitTermination(Mockito.anyLong(), Mockito.any())).thenReturn(true)

        shutdownListener.onApplicationEvent(mock(ContextClosedEvent.class))
        verify(mockedConnector, Mockito.times(1)).pause()
        verify(mockedExecutor, times(1)).awaitTermination(Mockito.eq(GRACEFUL_TIMEOUT), Mockito.eq(TimeUnit.SECONDS))
        verify(mockedExecutor, Mockito.times(1)).shutdown()
        verify(mockedExecutor, Mockito.never()).shutdownNow()
    }

    @Test
    public void "graceful shutdown failed, forced  successed"() {
        ThreadPoolExecutor mockedExecutor = mock(ThreadPoolExecutor.class)
        when(mockedProtocolHandler.getExecutor()).thenReturn(mockedExecutor)

        when(mockedExecutor.awaitTermination(Mockito.anyLong(), Mockito.any()))
                .thenReturn(false)
                .thenReturn(true)

        shutdownListener.onApplicationEvent(mock(ContextClosedEvent.class))
        verify(mockedConnector, Mockito.times(1)).pause()
        verify(mockedExecutor, Mockito.times(1)).shutdown()
        verify(mockedExecutor).awaitTermination(Mockito.eq(GRACEFUL_TIMEOUT), Mockito.eq(TimeUnit.SECONDS))
        verify(mockedExecutor).awaitTermination(Mockito.eq(FORCEFUL_TIMEOUT), Mockito.eq(TimeUnit.SECONDS))
        verify(mockedExecutor, Mockito.times(1)).shutdownNow()
    }

    @Test
    public void "graceful shutdown failed, forced  failed"() {
        ThreadPoolExecutor mockedExecutor = mock(ThreadPoolExecutor.class)
        when(mockedProtocolHandler.getExecutor()).thenReturn(mockedExecutor)

        when(mockedExecutor.awaitTermination(Mockito.anyLong(), Mockito.any()))
                .thenReturn(false)
                .thenReturn(false)

        shutdownListener.onApplicationEvent(mock(ContextClosedEvent.class))
        verify(mockedConnector, Mockito.times(1)).pause()
        verify(mockedExecutor, Mockito.times(1)).shutdown()
        verify(mockedExecutor).awaitTermination(Mockito.eq(GRACEFUL_TIMEOUT), Mockito.eq(TimeUnit.SECONDS))
        verify(mockedExecutor).awaitTermination(Mockito.eq(FORCEFUL_TIMEOUT), Mockito.eq(TimeUnit.SECONDS))
        verify(mockedExecutor, Mockito.times(1)).shutdownNow()
    }
}
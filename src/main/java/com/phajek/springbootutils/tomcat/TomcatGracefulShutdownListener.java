package com.phajek.springbootutils.tomcat;

import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Spring {@link ApplicationListener} which handles graceful shutdown of Tomcat application server.
 * This class ensures, that once Spring generates {@link ContextClosedEvent} (i.e. receives "SIGTERM" signal) it:
 * <ul>
 * <li>Stops tomcat connector (Tomcat will not accept any connections)</li>
 * <li>Shuts down Tomcat connector thread pool and waits until all requests have been served. If graceful shutdown times out, application will
 * process to interrupt active processing threads. Threads needs to interrupt in this timeout otherwise will be killed
 * </li>
 * </ul>
 * <p>
 * Stopping Tomcat in accepting connections is needed as Container platform (Kubernetes or OpenShift) can still send
 * requests to the container even if it's in termination process.
 *
 * @author Petr Hajek
 * @see <a href="https://github.com/openshift/origin/issues/18914">https://github.com/openshift/origin/issues/18914</a>
 * @see <a href="https://github.com/spring-projects/spring-boot/issues/4657#issuecomment-161354811">https://github.com/spring-projects/spring-boot/issues/4657#issuecomment-161354811</a>
 */
public class TomcatGracefulShutdownListener implements ApplicationListener<ContextClosedEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomcatGracefulShutdownListener.class);

    private final Duration gracefulShutdownTimeout;
    private final Duration forcefulShutdownTimeout;

    /**
     * Constructor of TomcatGracefulShutdownListener.
     *
     * @param gracefulShutdownTimeout Timeout for attempts of Adapter Graceful Shutdown.
     * @param forcefulShutdownTimeout Timeout for forced shutdown if graceful shutdown times out. (If graceful shutdown times out, application will
     *                                process to interrupt active processing threads. Threads needs to interrupt in this timeout otherwise will be killed).
     */
    public TomcatGracefulShutdownListener(Duration gracefulShutdownTimeout, Duration forcefulShutdownTimeout) {
        Objects.requireNonNull(gracefulShutdownTimeout);
        Objects.requireNonNull(forcefulShutdownTimeout);
        this.gracefulShutdownTimeout = gracefulShutdownTimeout;
        this.forcefulShutdownTimeout = forcefulShutdownTimeout;
    }

    /**
     * Constructor of TomcatGracefulShutdownListener with default values:
     * <ul>
     * <li>gracefulShutdownTimeout = 30s</li>
     * <li>forcefulShutdownTimeout = 10s</li>
     * </ul>
     */
    public TomcatGracefulShutdownListener() {
        this.gracefulShutdownTimeout = Duration.ofSeconds(30);
        this.forcefulShutdownTimeout = Duration.ofSeconds(10);
    }

    @Autowired
    private ServletWebServerApplicationContext webServerContext;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        LOGGER.info("Gracefully shutting-down Tomcat server.");
        if (webServerContext.getWebServer() instanceof TomcatWebServer == false) {
            LOGGER.error("Graceful shutdown failed: Webserver is not Tomcat. Expected class '{}', but is '{}'", TomcatWebServer.class.getName(), webServerContext.getWebServer().getClass().getName());
            return;
        }
        Connector connector = ((TomcatWebServer) webServerContext.getWebServer()).getTomcat().getConnector();

        // Pauses Tomcat so it'll not accept any new connections.
        connector.pause();
        Executor executor = connector.getProtocolHandler().getExecutor();
        if (executor instanceof ThreadPoolExecutor) {
            shutdownThreadPool((ThreadPoolExecutor) executor);
        } else {
            LOGGER.error("Graceful shutdown failed: Executor of Tomcat connector is '{}', but must be '{}'", executor.getClass().getName(), ThreadPoolExecutor.class.getName());
        }
    }

    private void shutdownThreadPool(ThreadPoolExecutor threadPoolExecutor) {
        try {
            threadPoolExecutor.shutdown();
            if (threadPoolExecutor.awaitTermination(gracefulShutdownTimeout.getSeconds(), TimeUnit.SECONDS) == false) {
                LOGGER.warn("Tomcat thread pool did not shut down gracefully within '{}' seconds. Proceeding with forceful shutdown", gracefulShutdownTimeout.getSeconds());

                threadPoolExecutor.shutdownNow();

                if (threadPoolExecutor.awaitTermination(forcefulShutdownTimeout.getSeconds(), TimeUnit.SECONDS) == false) {
                    LOGGER.error("Graceful shutdown failed: Tomcat thread pool did not terminate within '{}' ", forcefulShutdownTimeout.getSeconds());
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

}
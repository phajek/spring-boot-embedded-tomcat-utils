# Spring Boot Embedded Tomcat Utils
Small utilities for Spring Boot Embedded Tomcat.

Please note: since this is small project, it's suggested not to use it
as Maven dependency, but rather copy-paste classes needed files to your project.

## Features of this project
###  Embedded Tomcat Graceful Shutdown optimized for Kubernetes/OpenShift
This project contains logic for graceful shutdown on Embedded Tomcat which is optimized
for Kubernetes/Openshift Container platforms (it has been tested on OpenShift but should
work on Kubernetes as well).

Please note, that base of this logic is from Spring forum: [https://github.com/spring-projects/spring-boot/issues/4657#issuecomment-161354811](https://github.com/spring-projects/spring-boot/issues/4657#issuecomment-161354811)
and [https://github.com/spring-projects/spring-boot/issues/4657#issuecomment-422561557](https://github.com/spring-projects/spring-boot/issues/4657#issuecomment-422561557)

The graceful shutdown ensures that, when the application is going down:
* it'll not end immediately, but will wait to finnish processing of currently accepted
requests (i.e. will not end in a middle of request processing).
* it'll not accept any new HTTP connections if the graceful shutdown has started.
  * This is necessary for OpenShift/Kubernetes Container platforms as otherwise it might happen, that if
those platforms are killing container (pod) they can be still sending requests to container which
is being killed. So during the deployment or scaling of containers the clients might
get errors. For more details check: [https://hackernoon.com/graceful-shutdown-in-kubernetes-435b98794461](https://hackernoon.com/graceful-shutdown-in-kubernetes-435b98794461)
or [https://bugzilla.redhat.com/show_bug.cgi?id=1573207#c5](https://bugzilla.redhat.com/show_bug.cgi?id=1573207#c5)

The graceful shutdown works like this:
* Once Spring receives ContextClosedEvent (it's going down), this this lib
will start graceful shutdown:
  * Pauses tomcat connector (Tomcat will not accept any HTTP connections)
  * Shuts down Tomcat connector thread pool and waits until all requests have been served.
  * If graceful shutdown times out, application will process to interrupt active processing threads. Threads needs to interrupt in this forceful timeout otherwise will be killed.

#### How to use this
Whole Graceful Shutdown logic can be found in the class [TomcatGracefulShutdownListener](src/main/java/com/phajek/springbootutils/tomcat/TomcatGracefulShutdownListener.java)
it's just necessary to use mentioned class as Spring Bean - so for example put into you
Configuration class following:
```
    /**
     * Spring Event listener, which handles graceful shutdown of the Tomcat connections - takes care, that application
     * will not breach opened connections.
     *
     * @return Spring Event listener
     */
    @Bean
    public TomcatGracefulShutdownListener tomcatGracefulShutdownListener() {
        return new TomcatGracefulShutdownListener();
    }
```
please note, that if you're using Spring Integration Testing (`@SpringBootTest`) you
need to ensure, that `TomcatGracefulShutdownListener` bean is not being intialized as
it requires Embedded Tomcat Context which is not being used in Spring Test Context.
To ensure this you can for example start tests with "test" profile and then init `TomcatGracefulShutdownListener` bean
only if "test" profile is not active.
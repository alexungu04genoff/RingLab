package dev.ringlab;

import dev.ringlab.application.build.BuildPublicationChanged;
import jakarta.enterprise.event.Event;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Records the only CDI Event operation used by the build application service. */
public final class PublicationEvents {
  public final List<BuildPublicationChanged> fired = new ArrayList<>();

  @SuppressWarnings("unchecked")
  public Event<BuildPublicationChanged> event() {
    return (Event<BuildPublicationChanged>) Proxy.newProxyInstance(Event.class.getClassLoader(),
        new Class<?>[] {Event.class}, (proxy, method, args) -> {
          if (!method.getName().equals("fire")) throw new AssertionError(method.getName());
          fired.add((BuildPublicationChanged) args[0]);
          return null;
        });
  }
}

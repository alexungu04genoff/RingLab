package dev.ringlab.application.build;

import dev.ringlab.application.ForbiddenException;
import dev.ringlab.application.NotFoundException;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.BuildVisibility;
import java.util.UUID;

/** One publication boundary for reads, community participation and owner mutations. */
public final class BuildAccessPolicy {
  private BuildAccessPolicy() {}

  public static boolean publiclyReadable(Build build) {
    return build.visibility() == BuildVisibility.PUBLIC;
  }

  public static Build requirePublic(Build build) {
    if (!publiclyReadable(build)) throw NotFoundException.missing("Build");
    return build;
  }

  public static Build requireReadable(Build build, UUID viewer) {
    if (!publiclyReadable(build) && !build.authorId().equals(viewer))
      throw NotFoundException.missing("Build");
    return build;
  }

  public static Build requireOwner(Build build, UUID actor) {
    requireReadable(build, actor);
    ForbiddenException.requireOwner(build.authorId(), actor);
    return build;
  }
}

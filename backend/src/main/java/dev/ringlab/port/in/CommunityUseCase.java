package dev.ringlab.port.in;

import dev.ringlab.domain.gamedata.PassiveStatsResult;

public interface CommunityUseCase {
  CommunitySnapshot get();

  /** Calculate presentation facts only when needed, preserving conditional-response and cache behavior. */
  PassiveStatsResult passiveStats(CommunitySnapshot.Entry entry);
}

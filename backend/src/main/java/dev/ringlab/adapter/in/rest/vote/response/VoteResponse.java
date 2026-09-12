package dev.ringlab.adapter.in.rest.vote.response;

import dev.ringlab.application.vote.VoteService;

public record VoteResponse(long score, long upvotes, long downvotes, int myVote) {
  public static VoteResponse from(VoteService.Result result) {
    return new VoteResponse(result.score(), result.upvotes(), result.downvotes(), result.myVote());
  }
}

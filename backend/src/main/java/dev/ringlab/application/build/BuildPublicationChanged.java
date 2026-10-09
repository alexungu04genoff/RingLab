package dev.ringlab.application.build;

/** Delivered after commit so cached public snapshots cannot outlive a publication change. */
public record BuildPublicationChanged() {}

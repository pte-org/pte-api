package com.pte.itembank.internal.generation.media;

/**
 * Where a job's first audio question stands in its batch: the batch's seed and how many audio questions come before it.
 * It lets every job of one batch pick its voice from one shared sequence instead of on its own.
 */
public record BatchPosition(long seed, int offset) { }

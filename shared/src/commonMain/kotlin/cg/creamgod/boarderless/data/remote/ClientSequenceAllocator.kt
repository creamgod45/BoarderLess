package cg.creamgod.boarderless.data.remote

/** High-water reservation, not an acknowledged server checkpoint. Configure before repository creation. */
internal interface ClientSequenceAllocator {
    fun reserve(
        scope: PendingSubmissionScope,
        count: Int,
    ): LongRange

    fun advance(
        scope: PendingSubmissionScope,
        sequence: Long,
    )
}

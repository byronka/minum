AI test fix report
==================

This is a status report of what I did for each of the tests that was
created from Matt's AI test, for your interest and information.  I
have written comments on each AI test method.

Important note: I used the term "slop" for test cases that advised me
poorly.  I believe this term is more appropriate than any other term.
If this were advice coming from another developer, especially a
senior, I would want to dig deeper and maybe have a conversation to
understand where they are coming from.  People can be mistaken of
course.  However, this is not written by a real mind with a true sense
of the world. Instead, I am reading a statistically highly likely
string of words from a massive input dataset. The AI's documentation
carries an air of high conviction, yet after careful study, in the
"slop" cases its advice is empty and invalid.  A true senior
developer's words have a tone I could correlate to the correctness of
the advice - but that goes out the window when reading suggestions
about avoiding a "smuggled GET" that, after my analysis, is a nonsense
conclusion.  In this particular case, a smuggled GET is unimportant
because unlike most servers, mine does not handle proxying or chunked
encoding.  Because that is not a commonplace situation, it conflicts
with the AI's training set of commonplace situations, but the AI is
not able to apply real analysis to the situation like I can.  Thus ...
slop.

In short - every test marked "slop" would have at least created
unnecessary complexity with no benefit (e.g.
test_Finding_StopDiscardsQueuedWrites,
test_Finding_ChunkedRequestSmuggling), and at worst would have
instituted poor design choices deeply into the system (e.g.
test_Finding_TornAppendLogTailPreventsStartup).

    queue tests:    
        test_Finding_StopSetsStoppedStatusOnCleanPath                       bug     fixed
    
    database tests:                                                                     
        test_Finding_StopDiscardsQueuedWrites                               slop    ignored
        test_Finding_DeleteLeavesPhantomIndexEntry                          bug     fixed
        test_Finding_ConsolidationFlagStuckAfterFailure                     bug     fixed
        test_Finding_AppendFileRolloverLeaksFileDescriptor                  bug     fixed
        test_Finding_TornAppendLogTailPreventsStartup                       slop    adjusted test to be valuable
        test_Finding_FailedLoadRetryDuplicatesIndexEntries                  bug     fixed
        test_Finding_SharedSimpleDateFormatIsThreadSafe                     bug     fixed
            
    web tests:      
        test_Finding_ChunkedRequestSmuggling                                slop    ignored
        test_Finding_ChunkedRequestSmuggling_DetermineIfKeepAlive           slop    ignored
        test_Finding_RangeNotClampedToFileLength                            bug     fixed
        test_Finding_NullSentinelInQueryString                              bug     fixed
        test_Finding_NullSentinelBypassesDuplicateKeyCheck                  bug     fixed
        test_Finding_EmptyPathThrowsBadRequest                              bug     fixed
        test_Finding_StreamingResponseAdvertisesZeroContentLength           bug     deprecated method
        test_Finding_MultipartByteAccountingUsesCharsNotBytes               bug     fixed
        test_Finding_MultipartBoundaryNotTrimmedInRequest                   bug     fixed
        test_Finding_ContentLengthRejectsPlusPrefix                         bug     fixed
        test_Finding_TheBrigIsStoppedOnShutdown                             bug     fixed
            
    html parsing tests:     
        test_Finding_ScriptCloseTagIsCaseInsensitive                        slop    ignored
        test_Finding_DeeplyNestedHtmlDoesNotOverflowStack                   slop    ignored
            
    constants tests:        
        test_Finding_ExtractListOfEmptyStringIsEmpty                        bug     fixed
            
    utils tests:        
        test_Finding_SymlinkEscapesStaticDirectory                          slop    ignored
        test_Finding_AbsoluteStaticDirectoryPathRejected                    bug     fixed
        test_Finding_LruCacheReadRaceReturnsNull                            bug     fixed
        test_Finding_RingBufferContainsRepeatedPrefix                       bug     fixed
        test_Finding_SafeAttrEscapesAttributeBreakingCharacters             slop    ignored
        test_Finding_GenerateSecureRandomStringRejectsNonPositiveLength     bug     fixed
        test_Finding_SerializeHelperRejectsZeroArguments                    bug     fixed
    
    template tests:
        test_Finding_TemplateCapacityIntegerOverflow                        slop    ignored
        test_Finding_TemplateEmptyDataListThrows                            bug     fixed
        test_Finding_TemplateNullValueThrowsFrameworkException              bug     fixed


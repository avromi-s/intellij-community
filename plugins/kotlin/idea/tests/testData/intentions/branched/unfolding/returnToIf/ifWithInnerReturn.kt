fun test(b: Boolean): Int {
    while (true) {
        <caret>return if (b) {
            1
        } else {
            return 0
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions.UnfoldReturnToIfIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.UnfoldReturnToIfIntention
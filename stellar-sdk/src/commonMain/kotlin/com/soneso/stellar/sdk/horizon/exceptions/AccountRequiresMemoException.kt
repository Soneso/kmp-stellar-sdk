package com.soneso.stellar.sdk.horizon.exceptions

/**
 * AccountRequiresMemoException is thrown when a transaction is trying to submit an operation
 * to an account which requires a memo as defined in SEP-0029.
 *
 * @property accountId The account requiring the memo
 * @property operationIndex The operation where the account is the destination
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0029.md">SEP-0029</a>
 */
class AccountRequiresMemoException(
    message: String,
    val accountId: String,
    val operationIndex: Int
) : SdkException(message) {

    override fun toString(): String {
        return "AccountRequiresMemoException(accountId='$accountId', operationIndex=$operationIndex, message='$message')"
    }
}

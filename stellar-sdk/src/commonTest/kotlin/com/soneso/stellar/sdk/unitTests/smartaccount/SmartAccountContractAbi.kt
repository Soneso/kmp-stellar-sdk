package com.soneso.stellar.sdk.unitTests.smartaccount

/**
 * Smart Account Contract ABI Reference
 *
 * Documents the complete interface for the OpenZeppelin Stellar Smart Account contract,
 * including all functions, types, storage keys, error codes, and constants.
 *
 * Source: OpenZeppelin stellar-contracts (packages/accounts/)
 */
object SmartAccountContractAbi {

    // ========================================================================
    // Contract Functions
    // ========================================================================

    /**
     * Contract functions implementing the SmartAccount trait, ExecutionEntryPoint
     * trait, and Upgradeable trait.
     */
    object Functions {

        // -- SmartAccount Trait --

        /**
         * `__constructor(signers: Vec<Signer>, policies: Map<Address, Val>) -> void`
         *
         * Deploy constructor. Creates the default context rule (id=0) with the
         * provided initial signers and policies.
         */
        const val CONSTRUCTOR = "__constructor"

        /**
         * `__check_auth(signature_payload: BytesN<32>, signatures: Signatures,
         *               auth_contexts: Vec<Context>) -> Result<(), SmartAccountError>`
         *
         * Authorization verification entry point invoked by the Soroban runtime.
         * Validates signatures against the applicable context rule for each
         * authorization context, then runs all attached policies.
         */
        const val CHECK_AUTH = "__check_auth"

        /**
         * `get_context_rule(context_rule_id: u32) -> ContextRule`
         *
         * Query. Returns the context rule with the given ID.
         */
        const val GET_CONTEXT_RULE = "get_context_rule"

        /**
         * `get_context_rules(context_rule_type: ContextRuleType) -> Vec<ContextRule>`
         *
         * Query. Returns all context rules matching the given type.
         */
        const val GET_CONTEXT_RULES = "get_context_rules"

        /**
         * `get_context_rules_count() -> u32`
         *
         * Query. Returns the total number of context rules.
         */
        const val GET_CONTEXT_RULES_COUNT = "get_context_rules_count"

        /**
         * `add_context_rule(context_type: ContextRuleType, name: String,
         *                   valid_until: Option<u32>, signers: Vec<Signer>,
         *                   policies: Map<Address, Val>) -> ContextRule`
         *
         * State change (requires auth). Creates a new context rule with the
         * specified type, name, optional expiration ledger, signers, and policies.
         */
        const val ADD_CONTEXT_RULE = "add_context_rule"

        /**
         * `update_context_rule_name(context_rule_id: u32, name: String) -> ContextRule`
         *
         * State change (requires auth). Updates the name of an existing context rule.
         */
        const val UPDATE_CONTEXT_RULE_NAME = "update_context_rule_name"

        /**
         * `update_context_rule_valid_until(context_rule_id: u32,
         *                                  valid_until: Option<u32>) -> ContextRule`
         *
         * State change (requires auth). Updates the expiration ledger of an
         * existing context rule.
         */
        const val UPDATE_CONTEXT_RULE_VALID_UNTIL = "update_context_rule_valid_until"

        /**
         * `remove_context_rule(context_rule_id: u32) -> void`
         *
         * State change (requires auth). Removes an existing context rule.
         * The default context rule (id=0) cannot be removed.
         */
        const val REMOVE_CONTEXT_RULE = "remove_context_rule"

        /**
         * `add_signer(context_rule_id: u32, signer: Signer) -> void`
         *
         * State change (requires auth). Adds a signer to the specified context rule.
         */
        const val ADD_SIGNER = "add_signer"

        /**
         * `remove_signer(context_rule_id: u32, signer: Signer) -> void`
         *
         * State change (requires auth). Removes a signer from the specified context rule.
         */
        const val REMOVE_SIGNER = "remove_signer"

        /**
         * `add_policy(context_rule_id: u32, policy: Address, install_param: Val) -> void`
         *
         * State change (requires auth). Adds a policy contract to the specified
         * context rule with the given installation parameters.
         */
        const val ADD_POLICY = "add_policy"

        /**
         * `remove_policy(context_rule_id: u32, policy: Address) -> void`
         *
         * State change (requires auth). Removes a policy contract from the
         * specified context rule.
         */
        const val REMOVE_POLICY = "remove_policy"

        // -- ExecutionEntryPoint Trait --

        /**
         * `execute(target: Address, target_fn: Symbol, target_args: Vec<Val>) -> void`
         *
         * ExecutionEntryPoint trait. Requires auth. Invokes the specified function
         * on the target contract with the provided arguments on behalf of the
         * smart account.
         */
        const val EXECUTE = "execute"

        // -- Upgradeable Trait --

        /**
         * `upgrade(new_wasm_hash: BytesN<32>, operator: Address) -> void`
         *
         * Upgradeable trait. Requires auth. Upgrades the smart account contract
         * to a new WASM binary identified by its hash.
         */
        const val UPGRADE = "upgrade"
    }

    // ========================================================================
    // Types
    // ========================================================================

    /**
     * Signer (enum)
     *
     * Represents an authorized signer for a context rule.
     *
     * Variants:
     * - `Delegated(Address)` - Native Soroban address with built-in verification.
     *   The Soroban runtime handles signature verification for this signer type.
     * - `External(Address, Bytes)` - External verifier contract address + public key data.
     *   The verifier contract at the given address is called to verify signatures
     *   using the provided public key bytes.
     */
    object SignerType {
        const val DELEGATED = "Delegated"
        const val EXTERNAL = "External"
    }

    /**
     * Signatures: `Map<Signer, Bytes>`
     *
     * A map from signers to their corresponding signature bytes, wrapped in a
     * tuple struct in the Rust contract. Passed to `__check_auth` by the runtime.
     */
    object SignaturesType {
        const val DESCRIPTION = "Map<Signer, Bytes> wrapped in a tuple struct"
    }

    /**
     * ContextRuleType (enum)
     *
     * Defines what kind of authorization context a rule applies to.
     *
     * Variants:
     * - `Default` - Matches any context. The default rule (id=0) always has this type.
     * - `CallContract(Address)` - Matches calls to a specific contract address.
     * - `CreateContract(BytesN<32>)` - Matches contract creation with a specific WASM hash.
     */
    object ContextRuleTypeVariants {
        const val DEFAULT = "Default"
        const val CALL_CONTRACT = "CallContract"
        const val CREATE_CONTRACT = "CreateContract"
    }

    /**
     * ContextRule (struct)
     *
     * Represents a complete context rule with its configuration.
     *
     * Fields:
     * - `id: u32` - Unique identifier (0 = default rule, created at construction)
     * - `context_type: ContextRuleType` - What authorization contexts this rule matches
     * - `name: String` - Human-readable name for the rule
     * - `signers: Vec<Signer>` - List of authorized signers
     * - `policies: Vec<Address>` - List of policy contract addresses
     * - `valid_until: Option<u32>` - Optional expiration ledger sequence number
     */
    object ContextRuleFields {
        const val ID = "id"
        const val CONTEXT_TYPE = "context_type"
        const val NAME = "name"
        const val SIGNERS = "signers"
        const val POLICIES = "policies"
        const val VALID_UNTIL = "valid_until"
    }

    /**
     * Meta (struct)
     *
     * Metadata stored separately for each context rule.
     *
     * Fields:
     * - `context_type: ContextRuleType` - The rule's context type
     * - `name: String` - Human-readable name
     * - `valid_until: Option<u32>` - Optional expiration ledger sequence number
     */
    object MetaFields {
        const val CONTEXT_TYPE = "context_type"
        const val NAME = "name"
        const val VALID_UNTIL = "valid_until"
    }

    /**
     * WebAuthnSigData (struct)
     *
     * WebAuthn signature data for passkey-based authentication.
     *
     * Fields:
     * - `authenticator_data: Bytes` - CBOR-encoded authenticator data from the WebAuthn assertion
     * - `client_data: Bytes` - JSON client data from the WebAuthn assertion
     * - `signature: BytesN<64>` - The 64-byte signature (r || s) over the assertion
     */
    object WebAuthnSigDataFields {
        const val AUTHENTICATOR_DATA = "authenticator_data"
        const val CLIENT_DATA = "client_data"
        const val SIGNATURE = "signature"
    }

    // ========================================================================
    // Storage Keys
    // ========================================================================

    /**
     * SmartAccountStorageKey (enum)
     *
     * Storage layout for the smart account contract.
     *
     * Persistent storage:
     * - `Signers(u32)` -> `Vec<Signer>` - Signers for a context rule by rule ID
     * - `Policies(u32)` -> `Vec<Address>` - Policy addresses for a context rule by rule ID
     * - `Ids(ContextRuleType)` -> `Vec<u32>` - List of rule IDs for a context rule type
     * - `Meta(u32)` -> `Meta` - Metadata for a context rule by rule ID
     * - `Fingerprint(BytesN<32>)` -> `bool` - Replay protection fingerprints
     *
     * Instance storage:
     * - `NextId` -> `u32` - Next available context rule ID
     * - `Count` -> `u32` - Total number of context rules
     */
    object StorageKeys {
        const val SIGNERS = "Signers"
        const val POLICIES = "Policies"
        const val IDS = "Ids"
        const val META = "Meta"
        const val FINGERPRINT = "Fingerprint"
        const val NEXT_ID = "NextId"
        const val COUNT = "Count"
    }

    // ========================================================================
    // Policy Types
    // ========================================================================

    /**
     * Policy contracts and their storage/parameter types.
     *
     * All three policy types use `AccountContext(Address, u32)` as their
     * storage key, where the Address is the smart account address and the
     * u32 is the context rule ID.
     */
    object Policies {

        /**
         * SimpleThreshold Policy
         *
         * Requires a minimum number of valid signatures (M-of-N).
         *
         * Storage key: `AccountContext(Address, u32)`
         * Storage value: `SimpleThresholdAccountParams { threshold: u32 }`
         * Install param: `SimpleThresholdAccountParams { threshold: u32 }`
         */
        object SimpleThreshold {
            const val STORAGE_KEY = "AccountContext"

            object AccountParams {
                const val THRESHOLD = "threshold"
            }
        }

        /**
         * WeightedThreshold Policy
         *
         * Each signer has a weight; the sum of weights from valid signatures
         * must meet or exceed the threshold.
         *
         * Storage key: `AccountContext(Address, u32)`
         * Storage value: `WeightedThresholdAccountParams { signer_weights: Map<Signer, u32>, threshold: u32 }`
         * Install param: `WeightedThresholdAccountParams { signer_weights: Map<Signer, u32>, threshold: u32 }`
         */
        object WeightedThreshold {
            const val STORAGE_KEY = "AccountContext"

            object AccountParams {
                const val SIGNER_WEIGHTS = "signer_weights"
                const val THRESHOLD = "threshold"
            }
        }

        /**
         * SpendingLimit Policy
         *
         * Limits the total amount that can be spent within a rolling window
         * of ledgers.
         *
         * Storage key: `AccountContext(Address, u32)`
         * Storage value: `SpendingLimitData { spending_limit: i128, period_ledgers: u32,
         *                  cached_total_spent: i128, spending_history: Vec<SpendingEntry> }`
         * Install param: `SpendingLimitAccountParams { spending_limit: i128, period_ledgers: u32 }`
         */
        object SpendingLimit {
            const val STORAGE_KEY = "AccountContext"

            object AccountParams {
                const val SPENDING_LIMIT = "spending_limit"
                const val PERIOD_LEDGERS = "period_ledgers"
            }

            object StorageData {
                const val SPENDING_LIMIT = "spending_limit"
                const val PERIOD_LEDGERS = "period_ledgers"
                const val CACHED_TOTAL_SPENT = "cached_total_spent"
                const val SPENDING_HISTORY = "spending_history"
            }
        }
    }

    // ========================================================================
    // Error Codes
    // ========================================================================

    /**
     * SmartAccountError codes (3000-3012)
     *
     * Errors returned by the smart account contract.
     */
    object SmartAccountErrors {
        /** Context rule with the given ID does not exist. */
        const val CONTEXT_RULE_NOT_FOUND = 3000

        /** Signer is not part of the context rule. */
        const val SIGNER_NOT_FOUND = 3001

        /** Policy is not part of the context rule. */
        const val POLICY_NOT_FOUND = 3002

        /** Signer already exists in the context rule. */
        const val SIGNER_ALREADY_EXISTS = 3003

        /** Policy already exists in the context rule. */
        const val POLICY_ALREADY_EXISTS = 3004

        /** Cannot remove the default context rule (id=0). */
        const val CANNOT_REMOVE_DEFAULT = 3005

        /** The context rule has expired (valid_until exceeded). */
        const val CONTEXT_RULE_EXPIRED = 3006

        /** No matching context rule found for the authorization context. */
        const val NO_MATCHING_RULE = 3007

        /** Signature payload fingerprint was already used (replay protection). */
        const val FINGERPRINT_ALREADY_USED = 3008

        /** Cannot add more signers (MAX_SIGNERS exceeded). */
        const val TOO_MANY_SIGNERS = 3009

        /** Cannot add more policies (MAX_POLICIES exceeded). */
        const val TOO_MANY_POLICIES = 3010

        /** A context rule with the same type already exists. */
        const val DUPLICATE_CONTEXT_RULE_TYPE = 3011

        /** Cannot add more context rules (MAX_CONTEXT_RULES exceeded). */
        const val TOO_MANY_CONTEXT_RULES = 3012
    }

    /**
     * SimpleThresholdError codes (3200-3202)
     */
    object SimpleThresholdErrors {
        /** Threshold cannot be zero. */
        const val ZERO_THRESHOLD = 3200

        /** Threshold exceeds the number of signers. */
        const val THRESHOLD_EXCEEDS_SIGNERS = 3201

        /** Not enough valid signatures to meet the threshold. */
        const val THRESHOLD_NOT_MET = 3202
    }

    /**
     * WeightedThresholdError codes (3210-3213)
     */
    object WeightedThresholdErrors {
        /** Threshold cannot be zero. */
        const val ZERO_THRESHOLD = 3210

        /** A signer weight cannot be zero. */
        const val ZERO_WEIGHT = 3211

        /** Sum of all weights is less than the threshold (impossible to satisfy). */
        const val INSUFFICIENT_TOTAL_WEIGHT = 3212

        /** Weighted sum of valid signatures does not meet the threshold. */
        const val THRESHOLD_NOT_MET = 3213
    }

    /**
     * SpendingLimitError codes (3220-3224)
     */
    object SpendingLimitErrors {
        /** Spending limit cannot be zero or negative. */
        const val INVALID_SPENDING_LIMIT = 3220

        /** Period ledgers cannot be zero. */
        const val INVALID_PERIOD = 3221

        /** Transaction amount exceeds the remaining spending limit for the period. */
        const val LIMIT_EXCEEDED = 3222

        /** Could not determine the transfer amount from the authorization context. */
        const val AMOUNT_NOT_FOUND = 3223

        /** Arithmetic overflow in spending calculation. */
        const val OVERFLOW = 3224
    }

    /**
     * WebAuthnError codes (3110-3118)
     */
    object WebAuthnErrors {
        const val INVALID_CLIENT_DATA_JSON = 3110
        const val MISSING_CHALLENGE = 3111
        const val CHALLENGE_MISMATCH = 3112
        const val MISSING_TYPE = 3113
        const val INVALID_TYPE = 3114
        const val MISSING_ORIGIN = 3115
        const val INVALID_AUTHENTICATOR_DATA = 3116
        const val USER_NOT_PRESENT = 3117
        const val SIGNATURE_VERIFICATION_FAILED = 3118
    }

    // ========================================================================
    // Constants
    // ========================================================================

    /**
     * Contract limits.
     */
    object Constants {
        /** Maximum number of policy contracts per context rule. */
        const val MAX_POLICIES = 5

        /** Maximum number of signers per context rule. */
        const val MAX_SIGNERS = 15
    }

    // ========================================================================
    // Default Context Rule
    // ========================================================================

    /**
     * The default context rule is created during contract construction with id=0.
     * It has type [ContextRuleTypeVariants.DEFAULT] and matches any authorization
     * context. It cannot be removed. If no more specific context rule matches an
     * authorization context, the default rule is used as a fallback.
     */
    const val DEFAULT_CONTEXT_RULE_ID: UInt = 0u
}

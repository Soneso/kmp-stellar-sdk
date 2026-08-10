# frozen_string_literal: true

require 'minitest/autorun'
# The AST nodes the derivation inspects are Treetop syntax nodes, and referencing one
# autoloads a file that expects Treetop to be present.
require 'treetop'
require 'xdrgen'
require_relative '../lib/xdrgen/generators/kotlin_json_names'

# The SEP-0051 name derivation rules, exercised on the identifier shapes that decide them.
# tools/sep-51-oracle/name_map.rb checks the same code against the pinned reference CLI over
# the whole .x set; these tests pin the individual rules so a regression names itself.
class JsonNamesTest < Minitest::Test
  NAMES = Xdrgen::Generators::KotlinJsonNames

  def enum_name(identifier, siblings)
    NAMES.enum_member_json_name(identifier, siblings)
  end

  # -- Shared prefix ----------------------------------------------------------

  def test_single_member_enum_keeps_its_whole_identifier
    assert_equal 'public_key_type_ed25519',
      enum_name('PUBLIC_KEY_TYPE_ED25519', %w[PUBLIC_KEY_TYPE_ED25519])
  end

  def test_shared_prefix_is_stripped_at_its_last_underscore
    siblings = %w[SCV_BOOL SCV_VOID SCV_U32]
    assert_equal 'u32', enum_name('SCV_U32', siblings)
    assert_equal 'bool', enum_name('SCV_BOOL', siblings)
  end

  def test_prefix_without_an_underscore_strips_nothing
    siblings = %w[opINNER opBAD_AUTH opNO_ACCOUNT]
    assert_equal 'op_inner', enum_name('opINNER', siblings)
    assert_equal 'op_bad_auth', enum_name('opBAD_AUTH', siblings)
    assert_equal 'op_no_account', enum_name('opNO_ACCOUNT', siblings)
  end

  def test_transaction_result_codes_keep_their_tx_prefix
    siblings = %w[txSUCCESS txFAILED txTOO_EARLY]
    assert_equal 'tx_success', enum_name('txSUCCESS', siblings)
    assert_equal 'tx_too_early', enum_name('txTOO_EARLY', siblings)
  end

  def test_prefix_is_the_longest_shared_run_truncated_to_an_underscore
    siblings = %w[ENVELOPE_TYPE_TX_V0 ENVELOPE_TYPE_TX ENVELOPE_TYPE_AUTH]
    assert_equal 'tx_v0', enum_name('ENVELOPE_TYPE_TX_V0', siblings)
    assert_equal 'auth', enum_name('ENVELOPE_TYPE_AUTH', siblings)
  end

  def test_error_code_prefix
    siblings = %w[SCEC_ARITH_DOMAIN SCEC_INDEX_BOUNDS SCEC_INVALID_INPUT]
    assert_equal 'arith_domain', enum_name('SCEC_ARITH_DOMAIN', siblings)
  end

  # -- Digit-leading remainder ------------------------------------------------

  def test_digit_leading_remainder_keeps_the_prefixes_first_character
    siblings = %w[BINARY_FUSE_FILTER_8_BIT BINARY_FUSE_FILTER_16_BIT BINARY_FUSE_FILTER_32_BIT]
    assert_equal 'b8_bit', enum_name('BINARY_FUSE_FILTER_8_BIT', siblings)
    assert_equal 'b16_bit', enum_name('BINARY_FUSE_FILTER_16_BIT', siblings)
    assert_equal 'b32_bit', enum_name('BINARY_FUSE_FILTER_32_BIT', siblings)
  end

  def test_the_kept_character_comes_from_the_prefix_not_from_a_fixed_letter
    siblings = %w[SOROBAN_8_BIT SOROBAN_16_BIT]
    assert_equal 's8_bit', enum_name('SOROBAN_8_BIT', siblings)
  end

  # -- Camel-case boundaries --------------------------------------------------

  def test_camel_case_boundaries_are_split
    siblings = %w[WasmInsnExec MemAlloc]
    assert_equal 'wasm_insn_exec', enum_name('WasmInsnExec', siblings)
  end

  def test_a_lowercase_run_inside_an_uppercase_run_starts_a_new_word
    siblings = %w[IPv4 IPv6]
    assert_equal 'i_pv4', enum_name('IPv4', siblings)
    assert_equal 'i_pv6', enum_name('IPv6', siblings)
  end

  # -- Struct fields ----------------------------------------------------------

  def test_struct_field_is_snake_case_with_no_prefix_stripping
    assert_equal 'num_sponsored', NAMES.struct_field_json_name('numSponsored')
    assert_equal 'signer_sponsoring_i_ds', NAMES.struct_field_json_name('signerSponsoringIDs')
    assert_equal 'seq_num', NAMES.struct_field_json_name('seqNum')
  end

  def test_struct_field_named_type_keeps_that_spelling
    assert_equal 'type', NAMES.struct_field_json_name('type')
  end

  def test_struct_field_with_a_trailing_acronym
    assert_equal 'extend_footprint_ttl_op', NAMES.struct_field_json_name('extendFootprintTTLOp')
  end

  # -- Guarded shapes ---------------------------------------------------------

  def test_an_identifier_equal_to_its_prefix_raises
    error = assert_raises(NAMES::DerivationError) do
      enum_name('SCV_', %w[SCV_ SCV_U32])
    end
    assert_includes error.message, 'leaving no name to derive'
  end

  def test_a_remainder_of_error_raises
    error = assert_raises(NAMES::DerivationError) do
      enum_name('SCE_Error', %w[SCE_Error SCE_Other])
    end
    assert_includes error.message, 'reserved remainder'
  end

  # -- Union arm keys ---------------------------------------------------------

  FakeCase = Struct.new(:value_s)
  FakeMember = Struct.new(:name, :value)

  class FakeIntUnion
    def initialize(resolved) = @resolved = resolved
    def discriminant_type = nil
    def resolved_case(_kase) = @resolved
  end

  def test_integer_discriminated_arm_key_is_v_plus_the_case_value
    union = FakeIntUnion.new(nil)
    assert_equal 'v0', NAMES.union_arm_json_key(FakeCase.new('0'), union)
    assert_equal 'v3', NAMES.union_arm_json_key(FakeCase.new('3'), union)
  end

  def test_integer_discriminated_arm_key_resolves_a_named_case_label
    union = FakeIntUnion.new(FakeMember.new('STELLAR_VALUE_SIGNED', 1))
    assert_equal 'v1', NAMES.union_arm_json_key(FakeCase.new('STELLAR_VALUE_SIGNED'), union)
  end

  def test_a_negative_case_label_raises
    error = assert_raises(NAMES::DerivationError) do
      NAMES.union_arm_json_key(FakeCase.new('-1'), FakeIntUnion.new(nil))
    end
    assert_includes error.message, 'has no JSON key'
  end
end

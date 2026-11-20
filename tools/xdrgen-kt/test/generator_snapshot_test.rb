# frozen_string_literal: true

require 'minitest/autorun'
require 'fileutils'
require 'tmpdir'
require 'xdrgen'
require_relative '../lib/xdrgen/generators/kotlin'

class GeneratorSnapshotTest < Minitest::Test
  SNAPSHOT_DIR = File.expand_path('snapshots', __dir__)
  FIXTURE_DIR  = File.expand_path('fixtures', __dir__)

  # Generate once for all tests to avoid redundant compilation.
  @@output_dir = nil
  @@generated  = false

  def setup
    unless @@generated
      @@output_dir = Dir.mktmpdir('xdr_gen_test_')
      fixtures = Dir.glob(File.join(FIXTURE_DIR, '*.x')).sort
      raise "No .x fixtures found in #{FIXTURE_DIR}" if fixtures.empty?

      Xdrgen::Compilation.new(
        fixtures,
        output_dir: @@output_dir + '/',
        generator: Xdrgen::Generators::Kotlin,
        namespace: 'com.soneso.stellar.sdk.xdr',
        options: {}
      ).compile

      @@generated = true
    end
    @output_dir = @@output_dir
  end

  Minitest.after_run { FileUtils.rm_rf(@@output_dir) if @@output_dir }

  # -- Generator configuration tests ------------------------------------------

  def test_generator_class_is_defined
    assert defined?(Xdrgen::Generators::Kotlin), 'Kotlin generator class should be defined'
  end

  # -- Snapshot tests: Enum ---------------------------------------------------

  def test_snapshot_color_enum
    assert_snapshot 'ColorXdr.kt'
  end

  def test_snapshot_status_code_enum
    assert_snapshot 'StatusCodeXdr.kt'
  end

  # -- Snapshot tests: Struct -------------------------------------------------

  def test_snapshot_simple_struct
    assert_snapshot 'SimpleStructXdr.kt'
  end

  def test_snapshot_keyword_struct
    assert_snapshot 'KeywordStructXdr.kt'
  end

  def test_snapshot_nested_struct
    assert_snapshot 'NestedStructXdr.kt'
  end

  # -- Snapshot tests: Union --------------------------------------------------

  def test_snapshot_result_union
    assert_snapshot 'ResultXdr.kt'
  end

  def test_snapshot_result_type_enum
    assert_snapshot 'ResultTypeXdr.kt'
  end

  def test_snapshot_simple_status_enum
    assert_snapshot 'SimpleStatusXdr.kt'
  end

  def test_snapshot_simple_result_union
    assert_snapshot 'SimpleResultXdr.kt'
  end

  def test_snapshot_multi_case_union
    assert_snapshot 'MultiCaseUnionXdr.kt'
  end

  def test_snapshot_error_detail_struct
    assert_snapshot 'ErrorDetailXdr.kt'
  end

  # -- Snapshot tests: Bytes-backed string fields -----------------------------

  def test_snapshot_contract_executable_external_ref_struct
    assert_snapshot 'ContractExecutableExternalRefXdr.kt'
  end

  def test_snapshot_scval_union
    assert_snapshot 'SCValXdr.kt'
  end

  def test_snapshot_scval_type_enum
    assert_snapshot 'SCValTypeXdr.kt'
  end

  def test_snapshot_scstring_typedef
    assert_snapshot 'SCStringXdr.kt'
  end

  # -- Snapshot tests: Typedef ------------------------------------------------

  def test_snapshot_int32_typedef
    assert_snapshot 'Int32Xdr.kt'
  end

  def test_snapshot_uint32_typedef
    assert_snapshot 'Uint32Xdr.kt'
  end

  def test_snapshot_int64_typedef
    assert_snapshot 'Int64Xdr.kt'
  end

  def test_snapshot_uint64_typedef
    assert_snapshot 'Uint64Xdr.kt'
  end

  def test_snapshot_data_blob_typedef
    assert_snapshot 'DataBlobXdr.kt'
  end

  def test_snapshot_signed_count_typedef
    assert_snapshot 'SignedCountXdr.kt'
  end

  def test_snapshot_my_counter_typedef
    assert_snapshot 'MyCounterXdr.kt'
  end

  def test_snapshot_int_list_typedef
    assert_snapshot 'IntListXdr.kt'
  end

  def test_snapshot_short_string_typedef
    assert_snapshot 'ShortStringXdr.kt'
  end

  def test_snapshot_hash_typedef
    assert_snapshot 'HashXdr.kt'
  end

  # -- Snapshot tests: Constants ----------------------------------------------

  def test_snapshot_constants
    assert_snapshot 'Constants.kt'
  end

  # -- Snapshot tests: Primitive extensions ------------------------------------

  def test_snapshot_primitive_extensions
    assert_snapshot 'XdrPrimitiveExtensions.kt'
  end

  # -- Output sanity checks ---------------------------------------------------

  def test_generated_file_count
    generated = Dir.glob(File.join(@output_dir, '*.kt'))
    assert generated.length >= 20,
      "Expected at least 20 generated files, got #{generated.length}"
  end

  def test_no_extra_generated_files
    generated_names = Dir.glob(File.join(@output_dir, '*.kt')).map { |f| File.basename(f) }.sort
    snapshot_names  = Dir.glob(File.join(SNAPSHOT_DIR, '*.kt')).map { |f| File.basename(f) }.sort
    extra = generated_names - snapshot_names
    assert extra.empty?,
      "Generated files not covered by snapshots: #{extra.join(', ')} -- run 'make update-snapshots'"
  end

  def test_no_missing_generated_files
    generated_names = Dir.glob(File.join(@output_dir, '*.kt')).map { |f| File.basename(f) }.sort
    snapshot_names  = Dir.glob(File.join(SNAPSHOT_DIR, '*.kt')).map { |f| File.basename(f) }.sort
    missing = snapshot_names - generated_names
    assert missing.empty?,
      "Snapshot files not produced by generator: #{missing.join(', ')} -- stale snapshots?"
  end

  # -- Semantic checks --------------------------------------------------------

  def test_all_generated_files_have_marker
    Dir.glob(File.join(@output_dir, '*.kt')).each do |file|
      content = File.read(file)
      assert_includes content, 'Automatically generated by xdrgen',
        "#{File.basename(file)} is missing the 'Automatically generated by xdrgen' marker"
    end
  end

  def test_all_generated_files_have_package_declaration
    Dir.glob(File.join(@output_dir, '*.kt')).each do |file|
      content = File.read(file)
      assert_includes content, 'package com.soneso.stellar.sdk.xdr',
        "#{File.basename(file)} is missing the package declaration"
    end
  end

  def test_enum_has_decode_and_encode
    content = File.read(File.join(@output_dir, 'ColorXdr.kt'))
    assert_includes content, 'fun decode(reader: XdrReader): ColorXdr'
    assert_includes content, 'fun encode(writer: XdrWriter)'
  end

  def test_struct_has_decode_and_encode
    content = File.read(File.join(@output_dir, 'SimpleStructXdr.kt'))
    assert_includes content, 'fun decode(reader: XdrReader): SimpleStructXdr'
    assert_includes content, 'fun encode(writer: XdrWriter)'
  end

  def test_union_has_sealed_class
    content = File.read(File.join(@output_dir, 'ResultXdr.kt'))
    assert_includes content, 'sealed class ResultXdr'
    assert_includes content, 'abstract val discriminant'
  end

  def test_typedef_has_value_class
    content = File.read(File.join(@output_dir, 'Int32Xdr.kt'))
    assert_includes content, 'value class Int32Xdr'
    assert_includes content, '@kotlin.jvm.JvmInline'
  end

  def test_optional_field_generates_nullable_type
    content = File.read(File.join(@output_dir, 'NestedStructXdr.kt'))
    assert_includes content, 'val optionalInner: SimpleStructXdr?'
  end

  def test_variable_array_generates_list_type
    content = File.read(File.join(@output_dir, 'NestedStructXdr.kt'))
    assert_includes content, 'val items: List<SimpleStructXdr>'
  end

  def test_fixed_array_generates_array_type
    content = File.read(File.join(@output_dir, 'NestedStructXdr.kt'))
    assert_includes content, 'val fixedItems: Array<SimpleStructXdr>'
  end

  def test_opaque_generates_byte_array
    content = File.read(File.join(@output_dir, 'NestedStructXdr.kt'))
    assert_includes content, 'val rawData: ByteArray'
    assert_includes content, 'val fixedData: ByteArray'
  end

  def test_typedef_chain_resolves_correctly
    content = File.read(File.join(@output_dir, 'MyCounterXdr.kt'))
    assert_includes content, 'value class MyCounterXdr'
    assert_includes content, 'fun decode(reader: XdrReader): MyCounterXdr'
  end

  def test_array_typedef_generates_list_type
    content = File.read(File.join(@output_dir, 'IntListXdr.kt'))
    assert_includes content, 'value class IntListXdr'
    assert_includes content, 'List<Int>'
  end

  def test_union_without_default_has_else_throw
    content = File.read(File.join(@output_dir, 'SimpleResultXdr.kt'))
    assert_includes content, 'sealed class SimpleResultXdr'
    assert_includes content, 'throw IllegalArgumentException'
    refute_includes content, 'else -> {'
  end

  def test_kotlin_keywords_are_escaped_with_backticks
    content = File.read(File.join(@output_dir, 'KeywordStructXdr.kt'))
    assert_includes content, '`val`'
    assert_includes content, '`when`'
  end

  def test_enum_comments_are_preserved
    content = File.read(File.join(@output_dir, 'ColorXdr.kt'))
    assert_includes content, '/** Red color */'
    assert_includes content, '/** Green color */'
  end

  # -- XDR-JSON (SEP-0051) emission -------------------------------------------

  def test_every_type_carries_the_four_json_members
    types = Dir.glob(File.join(@output_dir, '*.kt')).map { |f| File.basename(f, '.kt') } -
            %w[Constants XdrPrimitiveExtensions]
    types.each do |type|
      content = File.read(File.join(@output_dir, "#{type}.kt"))
      assert_includes content, 'fun toXdrJson(): String', "#{type} is missing toXdrJson"
      assert_includes content, 'fun toXdrJsonElement(): JsonElement', "#{type} is missing toXdrJsonElement"
      assert_includes content, "fun fromXdrJson(json: String): #{type}", "#{type} is missing fromXdrJson"
      assert_includes content, "fun fromXdrJsonElement(element: JsonElement): #{type}",
        "#{type} is missing fromXdrJsonElement"
    end
  end

  # The depth limit is applied once, at whichever type the caller entered through. A nested
  # type is reached via fromXdrJsonTree, which must not re-check: that would rewalk the tree
  # at every level.
  def test_the_depth_limit_is_applied_once_per_decode
    assert_depth_limit_applied_once Dir.glob(File.join(@output_dir, '*.kt'))
  end

  # The same invariant over the Kotlin actually committed to the SDK, which is the output that
  # ships; the fixtures exercise only the constructs they happen to declare.
  def test_the_depth_limit_is_applied_once_per_decode_in_the_committed_output
    files = generated_sdk_files
    assert files.length > 400, "expected the generated SDK output, found #{files.length} files"
    assert_depth_limit_applied_once files
  end

  def test_every_committed_type_names_itself_in_one_file_private_constant
    generated_sdk_files.each do |file|
      content = File.read(file)
      name = File.basename(file, '.kt')
      next unless content.include?('fun toXdrJsonElement()')

      assert_includes content, %(private const val XDR_JSON_TYPE = "#{name}"),
        "#{name} does not declare its own type-name constant"
      assert_equal 1, content.scan('private const val XDR_JSON_TYPE').length,
        "#{name} declares the type-name constant more than once"
      assert_equal 1, content.scan(%("#{name}")).length,
        "#{name} repeats its own name as a literal outside the constant"
    end
  end

  def test_enum_members_carry_their_json_name
    content = File.read(File.join(@output_dir, 'ColorXdr.kt'))
    assert_includes content, 'enum class ColorXdr(val value: Int, internal val xdrJsonName: String)'
    assert_includes content, 'RED(0, "red"),'
    assert_includes content, 'BLUE(2, "blue");'
    assert_includes content, 'fun toXdrJsonElement(): JsonElement = XdrJson.name(xdrJsonName)'
  end

  def test_enum_prefix_is_stripped_in_the_emitted_json_name
    content = File.read(File.join(@output_dir, 'StatusCodeXdr.kt'))
    assert_includes content, 'STATUS_OK(0, "ok"),'
    assert_includes content, 'STATUS_NOT_FOUND(1, "not_found"),'
    assert_includes content, 'STATUS_UNKNOWN(-1, "unknown");'
  end

  def test_struct_emits_keys_in_declaration_order
    content = File.read(File.join(@output_dir, 'SimpleStructXdr.kt'))
    body = content[/fun toXdrJsonElement\(depth: Int\): JsonElement = XdrJson\.obj\(depth, XDR_JSON_TYPE\) \{\n(.*?)^  \}$/m, 1]
    keys = body.scan(/put\("([^"]+)"/).flatten
    assert_equal %w[count flags big_number unsigned_big active label], keys
  end

  def test_struct_reads_every_declared_key
    content = File.read(File.join(@output_dir, 'SimpleStructXdr.kt'))
    assert_includes content, 'val json = XdrJson.obj(element, XDR_JSON_TYPE, XDR_JSON_KEYS)'
    assert_includes content, 'XdrJson.field(json, "big_number", XDR_JSON_TYPE)'
  end

  # The decoder rejects a key that names no field, so each struct declares the keys it answers
  # to in the order it reads them.
  def test_struct_declares_the_keys_it_accepts
    content = File.read(File.join(@output_dir, 'SimpleStructXdr.kt'))
    assert_includes content,
                    'private val XDR_JSON_KEYS: Array<String> = ' \
                    'arrayOf("count", "flags", "big_number", "unsigned_big", "active", "label")'

    keyword = File.read(File.join(@output_dir, 'KeywordStructXdr.kt'))
    assert_includes keyword, 'private val XDR_JSON_KEYS: Array<String> = arrayOf("val", "when")'
  end

  def test_declared_maxima_are_validated_on_input
    content = File.read(File.join(@output_dir, 'NestedStructXdr.kt'))
    assert_includes content, '"items", maxLength = 10'
    assert_includes content, '"fixed_items", expectedLength = 3'
    assert_includes content, '"fixed_data", expectedLength = 4'
  end

  def test_binary_declared_maxima_are_emitted
    content = File.read(File.join(@output_dir, 'IntListXdr.kt'))
    assert_includes content, 'XDR array count ${value.size} exceeds maximum 100'

    binary_length_fixture do |dir|
      %w[BoundedXdr TextXdr TextAliasXdr BlobXdr BlobAliasXdr ItemsXdr ItemsAliasXdr ChoiceXdr].each do |type|
        content = File.read(File.join(dir, "#{type}.kt"))
        if type.include?('Alias')
          wrapped = type.sub('Alias', '')
          assert_includes content, "#{wrapped}.decode(reader)"
          assert_includes content, 'value.encode(writer)'
          next
        end
        if %w[BoundedXdr TextXdr ChoiceXdr].include?(type)
          assert_includes content, 'reader.readString(3)'
          assert_match(/writer.writeString\(\w+, 3\)/, content)
        end
        if %w[BoundedXdr BlobXdr ChoiceXdr].include?(type)
          assert_includes content, 'reader.readVariableOpaque(4)'
          assert_match(/writer.writeVariableOpaque\(\w+, 4\)/, content)
        end
        if %w[BoundedXdr ItemsXdr ChoiceXdr].include?(type)
          assert_includes content, 'reader.readArrayLength(LIMIT)'
          assert_match(/require\(\w+.size <= LIMIT\)/, content)
          assert_match(/XDR array count \$\{\w+.size\} exceeds maximum \$LIMIT/, content)
        end
      end
    end
  end

  def test_binary_unbounded_declarations_emit_no_maximum
    binary_length_fixture do |dir|
      content = File.read(File.join(dir, 'UnboundedXdr.kt'))
      %w[readString readVariableOpaque readArrayLength].each { |method| assert_includes content, "reader.#{method}()" }
      assert_includes content, 'writer.writeString(text)'
      assert_includes content, 'writer.writeVariableOpaque(blob)'
      assert_includes content, 'writer.writeInt(items.size)'
      refute_includes content, 'require('
    end
  end

  def test_optional_field_round_trips_through_null
    content = File.read(File.join(@output_dir, 'NestedStructXdr.kt'))
    assert_includes content, 'put("optional_inner", XdrJson.optional(optionalInner) { it.toXdrJsonElement(depth + 1) })'
    assert_includes content, 'XdrJson.optional(XdrJson.field(json, "optional_inner", XDR_JSON_TYPE))?.let'
  end

  def test_void_union_arm_is_a_bare_string_and_a_value_arm_is_an_object
    content = File.read(File.join(@output_dir, 'ResultXdr.kt'))
    assert_includes content, 'is Error -> XdrJson.obj(depth, XDR_JSON_TYPE) { put("error", value.toXdrJsonElement(depth + 1)) }'
    assert_includes content, 'is Void -> XdrJson.name(discriminant.xdrJsonName)'
    assert_includes content, '"ok" -> Void(ResultTypeXdr.RESULT_OK)'
    assert_includes content, '"error" -> Error(ErrorDetailXdr.fromXdrJsonTree(value))'
  end

  def test_union_without_a_default_arm_rejects_an_unknown_key
    content = File.read(File.join(@output_dir, 'SimpleResultXdr.kt'))
    assert_includes content, 'else -> XdrJson.unknownArm(XDR_JSON_TYPE, arm)'
  end

  def test_union_default_arm_derives_its_discriminant_from_the_key
    content = File.read(File.join(@output_dir, 'ResultXdr.kt'))
    assert_includes content, 'ResultTypeXdr.findXdrJsonName(arm) ?: XdrJson.unknownArm(XDR_JSON_TYPE, arm)'
  end

  def test_integer_discriminated_union_keys_its_arms_with_v
    content = File.read(File.join(@output_dir, 'MultiCaseUnionXdr.kt'))
    assert_includes content, 'is SimpleValue -> XdrJson.obj(depth, XDR_JSON_TYPE) { put("v0", XdrJson.int32(value)) }'
    assert_includes content, '"v1" -> TextValue(1,'
    assert_includes content, 'put("v$discriminant"'
    assert_includes content, 'XdrJson.intArm(XDR_JSON_TYPE, arm)'
  end

  def test_sixty_four_bit_values_are_strings_and_thirty_two_bit_values_are_numbers
    content = File.read(File.join(@output_dir, 'Int64Xdr.kt'))
    assert_includes content, 'internal fun toXdrJsonElement(depth: Int): JsonElement = XdrJson.int64(value)'
    content = File.read(File.join(@output_dir, 'Int32Xdr.kt'))
    assert_includes content, 'internal fun toXdrJsonElement(depth: Int): JsonElement = XdrJson.int32(value)'
  end

  def test_typedef_delegates_to_the_type_it_wraps
    content = File.read(File.join(@output_dir, 'MyCounterXdr.kt'))
    assert_includes content, 'internal fun toXdrJsonElement(depth: Int): JsonElement = value.toXdrJsonElement(depth)'
    assert_includes content, 'SignedCountXdr.fromXdrJsonTree(element)'
  end

  def test_opaque_is_hexadecimal
    content = File.read(File.join(@output_dir, 'HashXdr.kt'))
    assert_includes content, 'internal fun toXdrJsonElement(depth: Int): JsonElement = XdrJson.hex(value)'
    assert_includes content, 'expectedLength = 32'
  end

  # A leaf, an enum or an override type, writes one JSON string and has only the public writer,
  # which every position holding a leaf calls; an array around a leaf still opens a level. Any
  # other type takes the depth, a typedef of a leaf included, and the positions holding it pass
  # the depth on.
  def test_leaf_positions_call_the_public_writer_and_container_positions_pass_the_depth
    Dir.mktmpdir('xdr_json_leaves_') do |dir|
      source = File.join(dir, 'leaves.x')
      File.write(source, <<~XDR)
        enum Shade { LIGHT = 0, DARK = 1 };
        typedef Shade ShadeAlias;
        typedef ShadeAlias ShadeChain;
        typedef Shade ShadeList<4>;
        typedef opaque AssetCode4[4];
        typedef AssetCode4 CodeAlias;
        struct Box { int size; };
        struct Holder {
          ShadeChain chain; Shade pair[2]; Shade shades<3>; Shade* maybeShade; AssetCode4 code; CodeAlias aliasCode;
          Box box; Box boxPair[2]; Box boxes<3>; Box* maybeBox;
        };
        union Pick switch (int kind) { case 0: Shade shade; case 1: Box box; };
      XDR
      Xdrgen::Compilation.new([source], output_dir: dir + '/', generator: Xdrgen::Generators::Kotlin,
        namespace: 'com.soneso.stellar.sdk.xdr', options: {}).compile
      read = ->(type) { File.read(File.join(dir, "#{type}Xdr.kt")) }

      %w[Shade AssetCode4].each { |leaf| refute_includes read.(leaf), 'toXdrJsonElement(depth: Int)', leaf }
      assert_includes read.('AssetCode4'), 'fun toXdrJsonElement(): JsonElement = XdrJson.escapedString('
      {
        'ShadeAlias' => 'value.toXdrJsonElement()',
        'ShadeChain' => 'value.toXdrJsonElement(depth)',
        'ShadeList' => 'XdrJson.array(value, depth, XDR_JSON_TYPE) { it.toXdrJsonElement() }',
        'CodeAlias' => 'value.toXdrJsonElement()',
        'Box' => 'XdrJson.obj(depth, XDR_JSON_TYPE) {'
      }.each do |type, body|
        assert_includes read.(type), "  internal fun toXdrJsonElement(depth: Int): JsonElement = #{body}\n", type
      end
      [
        'put("chain", chain.toXdrJsonElement(depth + 1))',
        'put("pair", XdrJson.array(pair.asList(), depth + 1, XDR_JSON_TYPE) { it.toXdrJsonElement() })',
        'put("shades", XdrJson.array(shades, depth + 1, XDR_JSON_TYPE) { it.toXdrJsonElement() })',
        'put("maybe_shade", XdrJson.optional(maybeShade) { it.toXdrJsonElement() })',
        'put("code", code.toXdrJsonElement())',
        'put("alias_code", aliasCode.toXdrJsonElement(depth + 1))',
        'put("box", box.toXdrJsonElement(depth + 1))',
        'put("box_pair", XdrJson.array(boxPair.asList(), depth + 1, XDR_JSON_TYPE) { it.toXdrJsonElement(depth + 2) })',
        'put("boxes", XdrJson.array(boxes, depth + 1, XDR_JSON_TYPE) { it.toXdrJsonElement(depth + 2) })',
        'put("maybe_box", XdrJson.optional(maybeBox) { it.toXdrJsonElement(depth + 1) })'
      ].each { |line| assert_includes read.('Holder'), "    #{line}\n" }
      [
        'is Shade -> XdrJson.obj(depth, XDR_JSON_TYPE) { put("v0", value.toXdrJsonElement()) }',
        'is Box -> XdrJson.obj(depth, XDR_JSON_TYPE) { put("v1", value.toXdrJsonElement(depth + 1)) }'
      ].each { |line| assert_includes read.('Pick'), "    #{line}\n" }
    end
  end

  # The committed output holds the same rule: the leaves are exactly the enums and the override
  # types, and each position calls the writer of the type it declares with the depth exactly
  # when that type is not a leaf. A position names its value as a struct field, a union arm or
  # the value of a typedef, and the declaration of that name gives the type.
  def test_committed_positions_call_leaves_without_and_containers_with_the_depth
    overrides = Xdrgen::Generators::KotlinJsonOverrides.type_names
    files = generated_sdk_files.to_h { |file| [File.basename(file, '.kt'), File.read(file)] }
    files.select! { |_, content| content.include?('fun toXdrJsonElement(): JsonElement') }
    leaves = files.keys.reject { |type| files[type].include?('fun toXdrJsonElement(depth: Int)') }
    enums = files.keys.select { |type| files[type].include?("enum class #{type}(") }
    assert_equal (enums + overrides).sort, leaves.sort

    arm = / +data class (\w+)\(\n(?: +override val discriminant: \w+,\n)? */
    calls = Hash.new(0)
    files.each do |type, content|
      next if overrides.include?(type)

      declared = content.scan(/^(?:#{arm}|value class \w+\(| *)val `?(\w+)`?: ([^,)\n]+)/)
                        .to_h { |arm_name, field, kotlin| [arm_name || field, kotlin[/\w+Xdr/]] }
      content.each_line.grep(/\.toXdrJsonElement\(/) do |line|
        subject = line[/is (\w+) ->/, 1] || line[/put\("[^"]+", (?:XdrJson\.\w+\()?`?(\w+)/, 1] || 'value'
        written = declared.fetch(subject) { flunk("#{type}: no declaration for #{subject} in #{line}") }
        depth = line[/\.toXdrJsonElement\(([^)]*)\)/, 1]
        leaf = leaves.include?(written)
        assert_match(leaf ? /\A\z/ : /\Adepth( \+ \d)?\z/, depth, "#{type} writes #{written}: #{line}")
        calls[leaf] += 1
      end
    end
    assert calls[true].positive? && calls[false].positive?, "positions found: #{calls}"
  end

  # -- Bytes-backed string fields ---------------------------------------------

  # The override applies per listed field: the tag positions become ByteArray at all
  # five emission sites, while a sibling of the same string typedef keeps the wrapper.
  def test_bytes_backed_field_is_a_byte_array_at_every_emission_site
    struct = File.read(File.join(@output_dir, 'ContractExecutableExternalRefXdr.kt'))
    assert_includes struct, 'val tag: ByteArray'
    assert_includes struct, 'val tag = reader.readVariableOpaque()'
    assert_includes struct, 'writer.writeVariableOpaque(tag)'
    assert_includes struct, 'put("tag", XdrJson.escapedString(tag))'
    assert_includes struct, 'XdrJson.unescapeStringBytes(XdrJson.field(json, "tag", XDR_JSON_TYPE), XDR_JSON_TYPE, "tag")'
    refute_includes struct, 'SCStringXdr'

    union = File.read(File.join(@output_dir, 'SCValXdr.kt'))
    tag_arm = union[/data class ExecutableTag\(\n(.*?)\n  \)/m, 1]
    assert_equal '    val value: ByteArray', tag_arm
    assert_includes union, 'ExecutableTag(XdrJson.unescapeStringBytes(value, XDR_JSON_TYPE, "executable_tag"))'
    assert_includes union, 'is ExecutableTag -> XdrJson.obj(depth, XDR_JSON_TYPE) { put("executable_tag", XdrJson.escapedString(value)) }'
  end

  def test_a_string_position_beside_a_bytes_backed_one_keeps_the_wrapper_type
    union = File.read(File.join(@output_dir, 'SCValXdr.kt'))
    str_arm = union[/data class Str\(\n(.*?)\n  \)/m, 1]
    assert_equal '    val value: SCStringXdr', str_arm
    assert_includes union, '"string" -> Str(SCStringXdr.fromXdrJsonTree(value))'
  end

  def test_bounded_bytes_backed_string_emits_binary_maximum
    Dir.mktmpdir('xdr_bounded_bytes_string_') do |dir|
      source = File.join(dir, 'bounded.x')
      File.write(source, <<~XDR)
        typedef string BoundedString<7>;
        typedef BoundedString StringAlias;
        struct ContractExecutableExternalRef { StringAlias tag; };
      XDR
      Xdrgen::Compilation.new([source], output_dir: dir + '/', generator: Xdrgen::Generators::Kotlin,
        namespace: 'com.soneso.stellar.sdk.xdr', options: {}).compile
      content = File.read(File.join(dir, 'ContractExecutableExternalRefXdr.kt'))
      assert_includes content, 'val tag: ByteArray'
      assert_includes content, 'val tag = reader.readVariableOpaque(7)'
      assert_includes content, 'writer.writeVariableOpaque(tag, 7)'
    end
  end

  # A table entry naming a field no processed type declares is a stale entry; when the
  # caller opts in (the full-set run does), it raises instead of silently no-opping.
  def test_a_stale_bytes_backed_entry_raises_when_verification_is_requested
    Dir.mktmpdir('xdr_stale_entry_') do |dir|
      error = assert_raises(RuntimeError) do
        Xdrgen::Compilation.new(
          [File.join(FIXTURE_DIR, 'test_struct.x')],
          output_dir: dir + '/',
          generator: Xdrgen::Generators::Kotlin,
          namespace: 'com.soneso.stellar.sdk.xdr',
          options: { verify_bytes_backed_fields: true }
        ).compile
      end
      assert_match(/ContractExecutableExternalRef\.tag/, error.message)
      assert_match(/SCVal\.executable_tag/, error.message)
    end
  end

  def test_json_imports_are_emitted_only_where_used
    element_only = File.read(File.join(@output_dir, 'ColorXdr.kt'))
    assert_includes element_only, 'import kotlinx.serialization.json.JsonElement'
    refute_includes element_only, 'import kotlinx.serialization.json.buildJsonObject'

    both_branches = File.read(File.join(@output_dir, 'ResultXdr.kt'))
    assert_includes both_branches, 'import kotlinx.serialization.json.JsonObject'
    refute_includes both_branches, 'import kotlinx.serialization.json.buildJsonObject'

    value_arms_only = File.read(File.join(@output_dir, 'SimpleResultXdr.kt'))
    refute_includes value_arms_only, 'import kotlinx.serialization.json.JsonObject'
  end

  private

  def binary_length_fixture
    Dir.mktmpdir('xdr_binary_lengths_') do |dir|
      source = File.join(dir, 'lengths.x')
      File.write(source, <<~XDR)
        const LIMIT = 2;
        typedef string Text<3>;
        typedef opaque Blob<4>;
        typedef int Items<LIMIT>;
        typedef Text TextAlias;
        typedef Blob BlobAlias;
        typedef Items ItemsAlias;
        struct Bounded { string text<3>; opaque blob<4>; int items<LIMIT>; };
        struct Unbounded { string text<>; opaque blob<>; int items<>; };
        union Choice switch (int kind) {
          case 0: string text<3>;
          case 1: opaque blob<4>;
          case 2: int items<LIMIT>;
        };
      XDR
      Xdrgen::Compilation.new([source], output_dir: dir + '/', generator: Xdrgen::Generators::Kotlin,
        namespace: 'com.soneso.stellar.sdk.xdr', options: {}).compile
      yield dir
    end
  end

  SDK_XDR_DIR = File.expand_path(
    '../../../stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/xdr', __dir__
  )

  # The committed generated output, excluding the hand-maintained files that live beside it.
  def generated_sdk_files
    Dir.glob(File.join(SDK_XDR_DIR, '*.kt')).select do |file|
      File.read(file).include?('Automatically generated by xdrgen')
    end
  end

  def assert_depth_limit_applied_once(files)
    files.each do |file|
      content = File.read(file)
      name = File.basename(file, '.kt')
      assert_equal content.scan('XdrJson.checkDepth').length,
        content.scan('fun fromXdrJsonElement').length,
        "#{name} checks the depth somewhere other than fromXdrJsonElement"
      assert_equal content.scan('XdrJson.parse(').length, content.scan('fun fromXdrJson(').length,
        "#{name} parses text somewhere other than fromXdrJson"
    end
  end

  def assert_snapshot(filename)
    generated = File.join(@output_dir, filename)
    snapshot  = File.join(SNAPSHOT_DIR, filename)

    assert File.exist?(generated),
      "Generated file #{filename} should exist"
    assert File.exist?(snapshot),
      "Snapshot file #{filename} should exist -- run 'make update-snapshots'"

    generated_content = File.read(generated)
    snapshot_content  = File.read(snapshot)

    assert_equal snapshot_content, generated_content,
      "Generated #{filename} does not match snapshot -- run 'make update-snapshots' if the change is intentional"
  end
end

# frozen_string_literal: true

require_relative 'kotlin'

module Xdrgen
  module Generators
    # Emits the XDR-JSON (SEP-0051) round-trip suite for every generated type.
    #
    # The suite is emitted rather than written by hand for the same reason the types are: it
    # has to stay exhaustive across an XDR pin bump, so a type, union arm or enum member added
    # upstream arrives with its test already written.
    #
    # Subclasses the Kotlin generator so the two share one spelling of every name. The Kotlin
    # type name, the arm class name, the discriminant expression and the JSON key all come from
    # the methods the emitted production code is built from, so a test cannot assert a name the
    # generator does not produce.
    #
    # Output is grouped by the .x file each definition was parsed from, which keeps the file
    # count at the number of XDR source files rather than at the number of types.
    class KotlinJsonTests < Kotlin
      TEST_PACKAGE = 'com.soneso.stellar.sdk.unitTests.xdr.json'
      SDK_PACKAGE = 'com.soneso.stellar.sdk.xdr'

      # The nesting depth at which a sampled value stops descending.
      SAMPLE_DEPTH_CONSTANT = 'SAMPLE_DEPTH'
      SAMPLE_DEPTH_VALUE = 3

      # Elements a sampled variable-length array carries, and bytes a sampled variable-length
      # opaque carries. Four bytes is also the shortest payload a P strkey can carry.
      SAMPLE_ELEMENTS = 1
      SAMPLE_BYTES = 4

      # A key and a member name no generated type declares.
      UNKNOWN_NAME = 'not_a_name_this_type_declares'

      def generate
        @package = TEST_PACKAGE
        @groups = {}
        @definitions = []
        @file_index = build_file_index

        collect_definitions(@top)
        compute_finiteness

        emit_support_file
        @groups.each_value { |group| emit_group_file(group) }
      end

      private

      # -- Collection -------------------------------------------------------------------

      # Compilation#source concatenates the .x files with a newline between them, so a node's
      # interval into that text says which file it was parsed from.
      def build_file_index
        offset = 0
        @output.source_paths.map do |path|
          start = offset
          offset += File.read(path).length + 1
          basename = File.basename(path)
          { start: start, id: group_id_for(basename), source: basename }
        end
      end

      def group_id_for(basename)
        File.basename(basename, '.x').sub(/\AStellar-/, '').split('-').map(&:camelize).join
      end

      def group_of(defn)
        position = defn.interval.first
        entry = @file_index.reverse.find { |candidate| position >= candidate[:start] }
        raise "cannot place #{defn.name} in a source file" if entry.nil?

        entry
      end

      def collect_definitions(node)
        node.definitions.each { |defn| collect_definition(defn) }
        node.namespaces.each { |ns| collect_definitions(ns) }
      end

      def collect_definition(defn)
        if defn.respond_to?(:nested_definitions)
          defn.nested_definitions.each { |nested| collect_definition(nested) }
        end

        return unless testable?(defn)

        entry = group_of(defn)
        group = (@groups[entry[:id]] ||= { id: entry[:id], source: entry[:source], definitions: [] })
        group[:definitions] << defn
        @definitions << defn
      end

      def testable?(defn)
        defn.is_a?(AST::Definitions::Struct) ||
          defn.is_a?(AST::Definitions::Enum) ||
          defn.is_a?(AST::Definitions::Union) ||
          defn.is_a?(AST::Definitions::Typedef)
      end

      # -- Termination -------------------------------------------------------------------

      # A sample terminates because the sampler stops descending at SAMPLE_DEPTH: optional
      # members become null, variable-length arrays become empty, and a union selects an arm
      # whose members are all bounded. Which arm that is has to be known here, so this walks
      # the type graph to a fixed point and records, per type, whether a sample of it taken at
      # or below the depth limit is bounded.
      #
      # A type left unbounded by the fixed point has no finite value at all: every way of
      # building it nests within itself. The Stellar .x files contain no such type, and an XDR
      # change introducing one is rejected here rather than discovered as an exhausted stack.
      def compute_finiteness
        @bounded = {}
        loop do
          changed = false
          @definitions.each do |defn|
            type = name(defn)
            next if @bounded[type]

            if bounded_definition?(defn)
              @bounded[type] = true
              changed = true
            end
          end
          break unless changed
        end

        unbounded = @definitions.map { |defn| name(defn) }.reject { |type| @bounded[type] }
        return if unbounded.empty?

        raise "no finite sample exists for #{unbounded.join(', ')}: every way of building one " \
              'nests within itself through members the sampler cannot truncate'
      end

      def bounded_definition?(defn)
        case defn
        when AST::Definitions::Enum
          true
        when AST::Definitions::Union
          union_sample_entries(defn).any? { |entry| entry[:bounded].call(@bounded) }
        else
          hard_references(defn).all? { |type| @bounded[type] }
        end
      end

      # The types a definition reaches through members the sampler always fills in.
      def hard_references(defn)
        declarations_of(defn).reject { |decl| truncated_declaration?(decl) }
                             .filter_map { |decl| referenced_type_name(decl) }
      end

      def declaration_hard_references(decl)
        return [] if decl.nil? || truncated_declaration?(decl)

        [referenced_type_name(decl)].compact
      end

      def declarations_of(defn)
        case defn
        when AST::Definitions::Struct
          defn.members.map(&:declaration)
        when AST::Definitions::Union
          defn.arms.reject(&:void?).map(&:declaration)
        when AST::Definitions::Typedef
          [defn.declaration]
        else
          []
        end
      end

      def truncated_declaration?(decl)
        decl.is_a?(AST::Declarations::Optional) ||
          (decl.is_a?(AST::Declarations::Array) && !decl.fixed?)
      end

      def referenced_type_name(decl)
        return nil if decl.is_a?(AST::Declarations::Void)

        case decl.type
        when AST::Typespecs::Simple
          name(decl.type.resolved_type)
        when AST::Typespecs::Int, AST::Typespecs::UnsignedInt, AST::Typespecs::Hyper,
             AST::Typespecs::UnsignedHyper, AST::Typespecs::Bool, AST::Typespecs::String,
             AST::Typespecs::Opaque, AST::Typespecs::Float, AST::Typespecs::Double,
             AST::Typespecs::Quadruple
          nil
        else
          name(decl.type)
        end
      end

      # -- Support file -----------------------------------------------------------------

      def emit_support_file
        out = @output.open('XdrJsonSampleValues.kt')
        emit_header(out, ['kotlin.test.fail', 'kotlinx.serialization.json.JsonElement',
                          'kotlinx.serialization.json.JsonObject',
                          'kotlinx.serialization.json.JsonPrimitive'])

        out.puts <<~KOTLIN
          /**
           * The nesting depth at which a sampled value stops descending: variable-length arrays are
           * emitted empty and optional members null. Every cycle in the XDR type graph runs through
           * one of those two, so this is what makes a sample of a recursive type finite.
           */
          internal const val #{SAMPLE_DEPTH_CONSTANT}: Int = #{SAMPLE_DEPTH_VALUE}

          /**
           * Values a sample is built from. Each is a pure function of its seed, so a sampled instance
           * is the same on every target and on every run; nothing here is random.
           */
          internal fun sampleInt(seed: Int): Int = seed * 1103515245 + 12345

          internal fun sampleUInt(seed: Int): UInt = sampleInt(seed).toUInt()

          internal fun sampleLong(seed: Int): Long = sampleInt(seed).toLong() * 4294967311L + seed

          internal fun sampleULong(seed: Int): ULong = sampleLong(seed).toULong()

          internal fun sampleBoolean(seed: Int): Boolean = sampleInt(seed) and 1 == 0

          /**
           * Bytes that are never NUL, so a sampled asset code keeps its full width through the
           * trimming SEP-0051 applies to one. The trimmed forms are pinned by hand-written tests.
           */
          internal fun sampleBytes(seed: Int, length: Int): ByteArray =
              ByteArray(length) { (((seed + it) * 31 + 17).mod(255) + 1).toByte() }

          /**
           * Printable text carrying the one character the SEP-0051 escape ladder rewrites in
           * otherwise unremarkable input, truncated to what the member declares room for.
           */
          internal fun sampleString(seed: Int, maxLength: Int): String {
              val text = "sample\\\\text" + sampleInt(seed)
              return if (text.length <= maxLength) text else text.substring(0, maxLength)
          }

          /** The keys of a JSON object, in emission order. */
          internal fun jsonKeys(element: JsonElement): List<String> {
              val json = element as? JsonObject ?: fail("expected a JSON object, got $element")
              return json.keys.toList()
          }

          /**
           * Whether a value rendered as a single JSON string, which is the form SEP-0051 gives the
           * Stellar-specific types: strkeys, asset codes and the wide integers.
           */
          internal fun isJsonString(element: JsonElement): Boolean =
              element is JsonPrimitive && element.isString
        KOTLIN

        out.close
      end

      # -- Group file -------------------------------------------------------------------

      def emit_group_file(group)
        definitions = group[:definitions]
        out = @output.open("XdrJson#{group[:id]}Test.kt")
        emit_header(out, [
                      "#{SDK_PACKAGE}.*",
                      'kotlin.test.Test',
                      'kotlin.test.assertEquals',
                      'kotlin.test.assertFailsWith',
                      'kotlinx.serialization.json.JsonNull',
                      'kotlinx.serialization.json.JsonPrimitive',
                      'kotlinx.serialization.json.buildJsonObject'
                    ])

        definitions.each { |defn| emit_sample_builders(out, defn) }

        out.puts '/**'
        out.puts ' * XDR-JSON (SEP-0051) rendering, decoding and rejection behaviour of the types'
        out.puts " * generated from #{group[:source]}."
        out.puts ' */'
        out.puts "class XdrJson#{group[:id]}Test {"
        out.indent do
          definitions.each_with_index do |defn, index|
            out.puts
            emit_tests(out, defn, index)
          end
        end
        out.puts '}'

        out.close
      end

      def emit_header(out, imports)
        out.puts '// Automatically generated by xdrgen'
        out.puts '// DO NOT EDIT or your changes may be overwritten'
        out.puts
        out.puts "package #{@package}"
        out.puts
        imports.uniq.sort.each { |import| out.puts "import #{import}" }
        out.puts
      end

      # -- Sample builders --------------------------------------------------------------

      def emit_sample_builders(out, defn)
        type = name(defn)

        case defn
        when AST::Definitions::Enum
          out.puts "internal fun sample#{type}(seed: Int): #{type} = " \
                   "#{type}.entries[seed.mod(#{type}.entries.size)]"
        when AST::Definitions::Typedef
          out.puts "internal fun sample#{type}(seed: Int, depth: Int): #{type} ="
          out.indent { out.puts "#{type}(#{sample_expression(defn.declaration, 'seed')})" }
        when AST::Definitions::Struct
          emit_struct_sample(out, defn, type)
        when AST::Definitions::Union
          emit_union_samples(out, defn, type)
        end
        out.puts
      end

      # Seeds are spread far enough apart that neighbouring members of one type do not collide
      # once a nested builder adds its own member offsets.
      def sample_seed(index)
        index * 101 + 7
      end

      def emit_struct_sample(out, struct, type)
        if struct.members.empty?
          out.puts "internal fun sample#{type}(seed: Int, depth: Int): #{type} = #{type}()"
          return
        end

        out.puts "internal fun sample#{type}(seed: Int, depth: Int): #{type} = #{type}("
        out.indent do
          struct.members.each_with_index do |member, index|
            suffix = index < struct.members.length - 1 ? ',' : ''
            out.puts "#{sample_expression(member.declaration, seed_offset('seed', index))}#{suffix}"
          end
        end
        out.puts ')'
      end

      # Each constructible instance of a union gets its own builder so a test can name one arm
      # directly; the union's own builder rotates through them by seed. A union that can nest
      # within itself stops rotating at the depth limit and takes its bounded arm.
      def emit_union_samples(out, union, type)
        entries = union_sample_entries(union)

        entries.each do |entry|
          out.puts "internal fun sample#{entry[:builder]}(seed: Int, depth: Int): #{type} ="
          out.indent { out.puts entry[:construction].call('seed') }
          out.puts
        end

        bounded = entries.select { |entry| entry[:bounded].call(@bounded) }
        guard = bounded.length < entries.length
        anchor = bounded.find { |entry| entry[:void] } || bounded.first

        out.puts "internal fun sample#{type}(seed: Int, depth: Int): #{type} ="
        out.indent do
          if entries.length == 1
            out.puts "sample#{entries.first[:builder]}(seed, depth)"
          else
            out.puts "if (depth >= #{SAMPLE_DEPTH_CONSTANT}) sample#{anchor[:builder]}(seed, depth)" if guard
            out.puts "#{guard ? 'else ' : ''}when (seed.mod(#{entries.length})) {"
            out.indent do
              entries.each_with_index do |entry, index|
                label = index < entries.length - 1 ? index.to_s : 'else'
                out.puts "#{label} -> sample#{entry[:builder]}(#{seed_offset('seed', index)}, depth)"
              end
            end
            out.puts '}'
          end
        end
      end

      # One entry per constructible instance: every case of every explicit arm, plus the
      # default arm at a discriminant no explicit case claims.
      #
      # Two cases of one union can share an arm class — several void arms declared separately
      # collapse into a single Kotlin class carrying the discriminant — so a label taken from
      # the class name alone would name two builders the same. Where that happens the case
      # identifier is appended, and only there, to keep the common name short.
      def union_sample_entries(union)
        entries = union.normal_arms.flat_map do |arm|
          arm.cases.map { |kase| union_arm_entry(union, arm, kase) }
        end
        default = union_default_entry(union)
        entries << default if default

        type = name(union)
        duplicated = entries.map { |entry| entry[:label] }
                            .tally
                            .select { |_, count| count > 1 }
                            .keys
        entries.each do |entry|
          entry[:label] = "#{entry[:label]}#{entry[:case_name]}" if duplicated.include?(entry[:label])
          entry[:builder] = "#{type}#{entry[:label]}Arm"
        end
        entries
      end

      def union_arm_entry(union, arm, kase)
        type = name(union)
        class_name = arm.name.camelize
        discriminant = format_discriminant_value(kase, union)
        references = arm.void? ? [] : declaration_hard_references(arm.declaration)

        {
          case_name: kase.value_s.underscore.camelize,
          void: arm.void?,
          key: JSON_NAMES.union_arm_json_key(kase, union),
          label: class_name,
          bounded: ->(bounded) { references.all? { |referenced| bounded[referenced] } },
          construction: lambda { |seed_expr|
            arguments = []
            arguments << discriminant if union_json_dynamic_arm?(union, arm)
            arguments << sample_expression(arm.declaration, seed_expr) unless arm.void?
            arguments.empty? ? "#{type}.#{class_name}" : "#{type}.#{class_name}(#{arguments.join(', ')})"
          }
        }
      end

      # The default arm is reachable only at a discriminant no case names, so a test of it
      # needs one; a union whose cases already name every discriminant has no reachable default.
      def union_default_entry(union)
        arm = union.default_arm
        return nil unless arm.present?

        discriminant = unclaimed_discriminant(union)
        return nil if discriminant.nil?

        type = name(union)
        class_name = arm.name.camelize
        references = arm.void? ? [] : declaration_hard_references(arm.declaration)

        {
          case_name: 'Fallback',
          void: arm.void?,
          key: discriminant[:key],
          label: "#{class_name}Default",
          bounded: ->(bounded) { references.all? { |referenced| bounded[referenced] } },
          construction: lambda { |seed_expr|
            arguments = [discriminant[:expression]]
            arguments << sample_expression(arm.declaration, seed_expr) unless arm.void?
            "#{type}.#{class_name}(#{arguments.join(', ')})"
          }
        }
      end

      def unclaimed_discriminant(union)
        claimed = union.normal_arms.flat_map(&:cases).map(&:value_s)

        if union.discriminant_type.is_a?(AST::Definitions::Enum)
          enum = union.discriminant_type
          member = enum.members.find { |candidate| !claimed.include?(candidate.name) }
          return nil if member.nil?

          {
            expression: "#{name(enum)}.#{member.name}",
            key: JSON_NAMES.enum_member_json_name(member.name, enum.members.map(&:name))
          }
        else
          require_int_discriminant(union, name(union))
          used = union.normal_arms.flat_map(&:cases).map { |kase| kase.value.value.to_i }
          value = (used.max || -1) + 1
          { expression: value.to_s, key: "v#{value}" }
        end
      end

      def seed_offset(seed_expr, index)
        index.zero? ? seed_expr : "#{seed_expr} + #{index * 3}"
      end

      # The Kotlin expression that builds a sample of one declaration.
      def sample_expression(decl, seed_expr)
        case decl
        when AST::Declarations::Optional
          "if (depth < #{SAMPLE_DEPTH_CONSTANT}) #{sample_typespec(decl.type, seed_expr)} else null"
        when AST::Declarations::Array
          sample_array(decl, seed_expr)
        else
          sample_typespec(decl.type, seed_expr)
        end
      end

      def sample_array(decl, seed_expr)
        if kotlin_type_for_typespec(decl.type) == 'Byte'
          raise 'the Stellar .x files spell an array of raw bytes as opaque, not as an array of byte'
        end

        inner = sample_typespec(decl.type, "#{seed_expr} + it")
        if decl.fixed?
          _, size = decl.type.array_size
          "Array(#{size}) { #{inner} }"
        else
          declared = decl.size.blank? ? SAMPLE_ELEMENTS : decl.size.to_i
          elements = [SAMPLE_ELEMENTS, declared].min
          if elements.zero?
            'emptyList()'
          else
            "if (depth < #{SAMPLE_DEPTH_CONSTANT}) List(#{elements}) { #{inner} } else emptyList()"
          end
        end
      end

      def sample_typespec(typespec, seed_expr)
        case typespec
        when AST::Typespecs::Int
          "sampleInt(#{seed_expr})"
        when AST::Typespecs::UnsignedInt
          "sampleUInt(#{seed_expr})"
        when AST::Typespecs::Hyper
          "sampleLong(#{seed_expr})"
        when AST::Typespecs::UnsignedHyper
          "sampleULong(#{seed_expr})"
        when AST::Typespecs::Bool
          "sampleBoolean(#{seed_expr})"
        when AST::Typespecs::String
          "sampleString(#{seed_expr}, #{typespec.size.blank? ? 64 : typespec.size.to_i})"
        when AST::Typespecs::Opaque
          "sampleBytes(#{seed_expr}, #{sample_opaque_length(typespec)})"
        when AST::Typespecs::Float, AST::Typespecs::Double, AST::Typespecs::Quadruple
          raise_unsupported_json_typespec(typespec)
        when AST::Typespecs::Simple
          sample_reference(typespec.resolved_type, seed_expr)
        else
          sample_reference(typespec, seed_expr)
        end
      end

      def sample_opaque_length(typespec)
        return typespec.size.to_i if typespec.fixed?

        declared = typespec.size.blank? ? SAMPLE_BYTES : typespec.size.to_i
        [SAMPLE_BYTES, declared].min
      end

      def sample_reference(definition, seed_expr)
        type = name(definition)
        return "sample#{type}(#{seed_expr})" if definition.is_a?(AST::Definitions::Enum)

        "sample#{type}(#{seed_expr}, depth + 1)"
      end

      # -- Tests ------------------------------------------------------------------------

      def emit_tests(out, defn, index)
        case defn
        when AST::Definitions::Enum
          emit_enum_member_tests(out, defn)
          emit_enum_negative_test(out, defn)
        when AST::Definitions::Union
          emit_round_trip_test(out, defn, index)
          emit_union_arm_tests(out, defn, index)
          emit_union_negative_test(out, defn)
        when AST::Definitions::Struct
          emit_round_trip_test(out, defn, index)
          emit_struct_negative_test(out, defn)
        else
          emit_round_trip_test(out, defn, index)
          emit_typedef_negative_test(out, defn)
        end
      end

      # The tree is the subject: decoding what was emitted and emitting it again has to give
      # back the same tree and the same text. Comparing the decoded value with the original
      # instance instead would be a claim about two in-memory representations rather than
      # about the wire form.
      #
      # Two samples, taken at opposite ends of the depth limit, because an optional member and a
      # variable-length array each have two renderings and one sample only ever shows one of
      # them: the filled sample carries a value and at least one element, the truncated sample
      # carries null and the empty array.
      def emit_round_trip_test(out, defn, index)
        type = name(defn)
        seed = sample_seed(index)

        out.puts '@Test'
        out.puts "fun #{lower_camel(type)}RoundTripsThroughItsJsonTree() {"
        out.indent do
          out.puts "assertRoundTrip#{type}(sample#{type}(#{seed}, 0))"
          out.puts "assertRoundTrip#{type}(sample#{type}(#{seed}, #{SAMPLE_DEPTH_CONSTANT}))"
        end
        out.puts '}'
        out.puts

        out.puts "private fun assertRoundTrip#{type}(value: #{type}) {"
        out.indent do
          out.puts 'val tree = value.toXdrJsonElement()'
          out.puts "val text = #{type}.fromXdrJsonElement(tree).toXdrJson()"
          out.puts "assertEquals(tree, #{type}.fromXdrJson(text).toXdrJsonElement())"
          out.puts "assertEquals(text, #{type}.fromXdrJson(text).toXdrJson())"
        end
        out.puts '}'
        out.puts
      end

      def emit_enum_member_tests(out, enum)
        type = name(enum)
        identifiers = enum.members.map(&:name)

        enum.members.each do |member|
          json_name = JSON_NAMES.enum_member_json_name(member.name, identifiers)
          quoted = "\\\"#{json_name}\\\""

          out.puts '@Test'
          out.puts "fun #{lower_camel(type)}Renders#{member.name.underscore.camelize}As#{json_name.camelize}() {"
          out.indent do
            out.puts "val member = #{type}.#{member.name}"
            out.puts "assertEquals(#{member.value}, member.value)"
            out.puts "assertEquals(JsonPrimitive(\"#{json_name}\"), member.toXdrJsonElement())"
            out.puts "assertEquals(\"#{quoted}\", member.toXdrJson())"
            out.puts "assertEquals(member, #{type}.fromXdrJson(\"#{quoted}\"))"
            out.puts "assertEquals(member, #{type}.fromXdrJsonElement(JsonPrimitive(\"#{json_name}\")))"
          end
          out.puts '}'
          out.puts
        end
      end

      def emit_enum_negative_test(out, enum)
        type = name(enum)

        out.puts '@Test'
        out.puts "fun #{lower_camel(type)}RejectsAMemberItDoesNotDeclare() {"
        out.indent do
          out.puts "assertFailsWith<IllegalArgumentException> " \
                   "{ #{type}.fromXdrJsonElement(JsonPrimitive(\"#{UNKNOWN_NAME}\")) }"
          out.puts "assertFailsWith<IllegalArgumentException> { #{type}.fromXdrJsonElement(JsonNull) }"
        end
        out.puts '}'
        out.puts
      end

      # Each arm is asserted on its key as well as on its round trip: the key is the part of
      # the wire form a round trip cannot catch, since a wrong key both sides agree on still
      # round-trips.
      #
      # A type SEP-0051 renders in a Stellar-specific form carries no arm key at all — it is a
      # strkey, an asset code or a wide integer written as one JSON string — so what is
      # asserted there is that it stayed a single string. The exact text those types produce is
      # pinned by hand-written tests against the specification.
      def emit_union_arm_tests(out, union, index)
        type = name(union)
        seed = sample_seed(index)
        rendered_as_string = !JSON_OVERRIDES.for_type(type).nil?

        union_sample_entries(union).each_with_index do |entry, position|
          out.puts '@Test'
          out.puts "fun #{lower_camel(type)}Renders#{entry[:label]}ArmAs#{entry[:key].camelize}() {"
          out.indent do
            out.puts "val value = sample#{entry[:builder]}(#{seed + position * 5}, 0)"
            out.puts 'val tree = value.toXdrJsonElement()'
            if rendered_as_string
              out.puts 'assertEquals(true, isJsonString(tree))'
            elsif entry[:void]
              out.puts "assertEquals(JsonPrimitive(\"#{entry[:key]}\"), tree)"
            else
              out.puts "assertEquals(listOf(\"#{entry[:key]}\"), jsonKeys(tree))"
            end
            out.puts "val decoded = #{type}.fromXdrJsonElement(tree)"
            out.puts 'assertEquals(value.discriminant, decoded.discriminant)'
            out.puts 'assertEquals(tree, decoded.toXdrJsonElement())'
          end
          out.puts '}'
          out.puts
        end
      end

      def emit_union_negative_test(out, union)
        type = name(union)
        default = union.default_arm.present? ? union.default_arm : nil
        value_arms = union.normal_arms.any? { |arm| !arm.void? } || (default && !default.void?)
        void_arms = union.normal_arms.any?(&:void?) || (default && default.void?)

        out.puts '@Test'
        out.puts "fun #{lower_camel(type)}RejectsAnArmItDoesNotDeclare() {"
        out.indent do
          if value_arms
            out.puts 'assertFailsWith<IllegalArgumentException> {'
            out.indent do
              out.puts "#{type}.fromXdrJsonElement(buildJsonObject { put(\"#{UNKNOWN_NAME}\", JsonNull) })"
            end
            out.puts '}'
            out.puts 'assertFailsWith<IllegalArgumentException> {'
            out.indent do
              out.puts "#{type}.fromXdrJsonElement("
              out.indent do
                out.puts 'buildJsonObject {'
                out.indent do
                  out.puts "put(\"#{UNKNOWN_NAME}\", JsonNull)"
                  out.puts "put(\"#{UNKNOWN_NAME}_too\", JsonNull)"
                end
                out.puts '}'
              end
              out.puts ')'
            end
            out.puts '}'
          end
          if void_arms
            out.puts 'assertFailsWith<IllegalArgumentException> {'
            out.indent { out.puts "#{type}.fromXdrJsonElement(JsonPrimitive(\"#{UNKNOWN_NAME}\"))" }
            out.puts '}'
          end
          out.puts "assertFailsWith<IllegalArgumentException> { #{type}.fromXdrJsonElement(JsonNull) }"
        end
        out.puts '}'
        out.puts
      end

      def emit_struct_negative_test(out, struct)
        type = name(struct)

        out.puts '@Test'
        out.puts "fun #{lower_camel(type)}RejectsInputThatIsNotItsObject() {"
        out.indent do
          out.puts "assertFailsWith<IllegalArgumentException> { #{type}.fromXdrJsonElement(JsonNull) }"
          if struct.members.any?
            out.puts 'assertFailsWith<IllegalArgumentException> ' \
                     "{ #{type}.fromXdrJsonElement(buildJsonObject { }) }"
          end
        end
        out.puts '}'
        out.puts
      end

      # Null is the rendering of an unset optional, so a typedef of one accepts it; that shape
      # is rejected through an object instead, which no optional typedef in the set admits.
      def emit_typedef_negative_test(out, typedef)
        type = name(typedef)
        rejected = if typedef.declaration.is_a?(AST::Declarations::Optional)
                     'buildJsonObject { }'
                   else
                     'JsonNull'
                   end

        out.puts '@Test'
        out.puts "fun #{lower_camel(type)}RejectsInputOfTheWrongShape() {"
        out.indent do
          out.puts "assertFailsWith<IllegalArgumentException> { #{type}.fromXdrJsonElement(#{rejected}) }"
        end
        out.puts '}'
        out.puts
      end

      def lower_camel(type)
        type[0].downcase + type[1..]
      end
    end
  end
end

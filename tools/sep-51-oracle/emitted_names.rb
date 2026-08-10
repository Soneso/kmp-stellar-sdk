#!/usr/bin/env ruby
# frozen_string_literal: true

# Diffs the SEP-0051 XDR-JSON names that actually reached the generated Kotlin against
# name-map.json, the table name_map.rb derives from the .x sources and checks against the
# pinned reference CLI.
#
# This closes a gap name_map.rb cannot close. That tool proves the derivation module agrees
# with the reference, but it calls the module the same way for every type, so it can only
# ever confirm that the rules are right. It says nothing about how the generator invokes
# them: a wrong sibling list handed to the enum prefix computation, a key emitted under the
# wrong field, a struct whose keys come out in the wrong order, or a type-level override that
# stopped being applied would all leave name_map.rb green and ship wrong JSON. Reading the
# emitted Kotlin back is the only check that sees those.
#
# Usage:
#   ruby emitted_names.rb           Report the diff.
#   ruby emitted_names.rb --quiet   Print only the summary and any problems.
#
# Never writes anything. Exits 1 on any mismatch, missing name, extra name, or missing file.

require 'json'
require 'set'

SCRIPT_DIR = __dir__
ROOT = File.expand_path('../..', __dir__)
KOTLIN_DIR = File.join(ROOT, 'stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/xdr')
NAME_MAP = File.join(SCRIPT_DIR, 'name-map.json')

require File.join(ROOT, 'tools/xdrgen-kt/lib/xdrgen/generators/kotlin_json_overrides')

# Types whose JSON form is a single string rather than an object or a keyed arm, so they carry
# no derived names to diff. Read from the generator's own registry, so a type entering or
# leaving it is reported rather than silently excused.
OVERRIDDEN = Xdrgen::Generators::KotlinJsonOverrides.type_names.to_set

# An enum member declaration carries its wire name beside its numeric value.
ENUM_MEMBER = /^  ([A-Za-z_]\w*)\(-?\d+, "([^"]+)"\)[,;]$/
# The object an emitting struct builds, in emission order.
STRUCT_BODY = /fun toXdrJsonElement\(\): JsonElement = buildJsonObject \{\n(.*?)^  \}$/m
STRUCT_KEY = /^    put\("([^"]+)",/
# The arm keys a union dispatches on when decoding.
UNION_BODY = /internal fun fromXdrJsonTree\(element: JsonElement\).*?\n(?=  \}\n)/m
UNION_KEY = /^\s+"([^"]+)" -> /

class Diff
  attr_reader :problems, :counts

  def initialize
    @problems = []
    @counts = Hash.new(0)
  end

  def read(type)
    path = File.join(KOTLIN_DIR, "#{type}.kt")
    return File.read(path) if File.exist?(path)

    @problems << "#{type}: no generated file at #{path}"
    nil
  end

  def enums(entries)
    entries.each do |entry|
      type = entry['kmp_name']
      next if skip_overridden(type, entry, 'enum')

      source = read(type) or next
      emitted = source.scan(ENUM_MEMBER).to_h
      expected = entry['members'].to_h { |member| [member['identifier'], member['json']] }

      (expected.keys - emitted.keys).each { |id| @problems << "enum #{type}.#{id}: no member emitted" }
      (emitted.keys - expected.keys).each { |id| @problems << "enum #{type}.#{id}: emitted but not in the table" }
      expected.each do |identifier, json|
        @counts[:enum_members] += 1
        actual = emitted[identifier]
        next if actual.nil? || actual == json

        @problems << "enum #{type}.#{identifier}: emitted #{actual.inspect}, table #{json.inspect}"
      end
    end
  end

  # Struct keys are compared as an ordered list: SEP-0051 output is field declaration order,
  # so a reordering is a wire-format change even when the key set is unchanged.
  def structs(entries)
    entries.each do |entry|
      type = entry['kmp_name']
      next if skip_overridden(type, entry, 'struct')

      source = read(type) or next
      emitted = source[STRUCT_BODY, 1]&.scan(STRUCT_KEY)&.flatten
      expected = entry['fields'].map { |field| field['json'] }
      @counts[:struct_types] += 1
      @counts[:struct_keys] += expected.length

      if emitted.nil?
        @problems << "struct #{type}: emits no JSON object"
      elsif emitted != expected
        @problems << "struct #{type}: emitted #{emitted.inspect}, table #{expected.inspect}"
      end
    end
  end

  # Arm keys are compared as a set: a union renders one arm at a time, so their order in the
  # generated dispatch carries no wire meaning.
  def unions(entries)
    entries.each do |entry|
      type = entry['kmp_name']
      next if skip_overridden(type, entry, 'union')

      source = read(type) or next
      emitted = source[UNION_BODY]&.scan(UNION_KEY)&.flatten.to_a.to_set
      expected = entry['arms'].reject { |arm| arm['case'] == 'default' }
                              .map { |arm| arm['json'] }.to_set
      @counts[:union_types] += 1
      @counts[:union_arms] += expected.length
      next if emitted == expected

      @problems << "union #{type}: emitted #{emitted.to_a.sort.inspect}, " \
                   "table #{expected.to_a.sort.inspect}"
    end
  end

  # A type with a Stellar-specific rendering emits a single string and therefore no names. It
  # must still exist, and it must genuinely have stopped emitting an object.
  def skip_overridden(type, _entry, kind)
    return false unless OVERRIDDEN.include?(type)

    @counts[:overridden] += 1
    source = read(type)
    if source&.match?(STRUCT_BODY)
      @problems << "#{kind} #{type}: has a Stellar-specific rendering but still emits a JSON object"
    end
    true
  end

  # Every registered override must reach a generated file, so a renamed type cannot quietly
  # drop the rendering the registry holds for it.
  def overrides
    OVERRIDDEN.each do |type|
      next if File.exist?(File.join(KOTLIN_DIR, "#{type}.kt"))

      @problems << "override #{type}: registered in the generator but no generated file"
    end
    @counts[:overrides_registered] = OVERRIDDEN.length
  end
end

def main
  quiet = ARGV.include?('--quiet')

  unless File.exist?(NAME_MAP)
    abort "Missing #{NAME_MAP}. Run 'ruby tools/sep-51-oracle/name_map.rb' first."
  end

  table = JSON.parse(File.read(NAME_MAP))
  diff = Diff.new
  diff.enums(table['enums'])
  diff.structs(table['structs'])
  diff.unions(table['unions'])
  diff.overrides

  counts = diff.counts
  total = counts[:enum_members] + counts[:struct_keys] + counts[:union_arms]

  unless quiet
    puts
    puts 'Generated Kotlin vs name-map.json'
  end
  puts format('  enum members:   %d', counts[:enum_members])
  puts format('  struct types:   %d, %d field keys compared in emission order',
              counts[:struct_types], counts[:struct_keys])
  puts format('  union types:    %d, %d arm keys', counts[:union_types], counts[:union_arms])
  puts format('  string-rendered: %d types skipped, %d overrides registered',
              counts[:overridden], counts[:overrides_registered])
  puts format('  names compared: %d', total)
  puts format('  problems:       %d', diff.problems.length)

  unless diff.problems.empty?
    puts
    puts 'Problems:'
    diff.problems.each { |problem| puts "  #{problem}" }
    exit 1
  end
end

main if $PROGRAM_NAME == __FILE__

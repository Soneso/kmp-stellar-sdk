#!/usr/bin/env ruby
# frozen_string_literal: true

# Derives the SEP-0051 XDR-JSON name of every enum member, struct field and union
# arm directly from the .x sources, and checks the derivation against the pinned
# XDR-JSON reference CLI.
#
# The derivation rules come from the generator itself
# (tools/xdrgen-kt/lib/xdrgen/generators/kotlin_json_names.rb), so this check
# gates the code that actually emits the names rather than a second copy of it.
#
# Names are computed from the parsed .x AST rather than from generated Kotlin, so
# the tool keeps working when an XDR pin bump introduces types before any Kotlin
# exists for them.
#
# Usage:
#   ruby name_map.rb              Write name-map.json and type_map.json.
#   ruby name_map.rb --diff       Also probe the reference CLI for every enum
#                                 member and struct type it can resolve, and
#                                 report mismatches. Exits 1 if any name
#                                 disagrees with the reference.
#   ruby name_map.rb --check      Diff without writing anything, and report
#                                 whether the committed artefacts are current.
#                                 Exits 1 on a mismatch or a stale artefact,
#                                 so it can gate a build.
#   ruby name_map.rb --diff --quiet   Diff, printing only the summary.
#
# Exit codes, matching tools/sep-51-corpus/refresh_corpus.sh:
#   0  the derived names agree with the reference, and the artefacts are current
#   1  a name disagrees, or a committed artefact is stale
#   2  the reference CLI is missing or does not match the pin
#
# A caller that treats any non-zero exit as disagreement would report a missing
# CLI as a name mismatch, so the prerequisite failure is a distinct code.
#
# Types and enum members newer than the commit the reference CLI vendors are
# reported as unresolvable, never as mismatches; oracle-pin.json records both
# commits.

ENV['BUNDLE_GEMFILE'] ||= File.expand_path('../xdrgen-kt/Gemfile', __dir__)

require 'bundler/setup'
require 'xdrgen'
require 'base64'
require 'json'
require 'open3'
require 'set'
require 'tmpdir'
require_relative '../xdrgen-kt/lib/xdrgen/generators/kotlin_json_names'

Sep51Names = Xdrgen::Generators::KotlinJsonNames
DerivationError = Sep51Names::DerivationError

# The reference CLI is absent or does not match the pin. Distinct from a name
# disagreement, because the two call for different responses: install the pinned
# build, versus fix the derivation.
PrerequisiteError = Class.new(StandardError)

SCRIPT_DIR = __dir__
XDR_DIR = File.expand_path('../xdrgen-kt/xdr', __dir__)
PIN_FILE = File.join(SCRIPT_DIR, 'oracle-pin.json')

# Source files whose types this SDK does not generate. Kept identical to the
# generator's exclusion list so the two see the same type universe.
EXCLUDED_SOURCES = %w[
  Stellar-exporter.x
  Stellar-internal.x
  Stellar-overlay.x
].freeze

# Structs that SEP-0051 renders as a single JSON string rather than an object:
# the integer-parts types become one base-10 decimal, and the account and
# signed-payload types become strkeys. They carry no JSON field names, so the
# field-name diff does not apply to them and reports them separately. A struct
# appearing here unexpectedly means a new type needs a string rendering; one
# disappearing means a type that used to be a string is now an object.
STRING_RENDERED_STRUCTS = %w[
  Int128Parts
  Int256Parts
  UInt128Parts
  UInt256Parts
  MuxedEd25519Account
  med25519
  ed25519SignedPayload
].freeze

# Walks the parsed .x AST and produces the full name table.
class NameMapBuilder
  include Xdrgen::AST

  def initialize(top)
    @top = top
    @enums = []
    @structs = []
    @unions = []
  end

  attr_reader :enums, :structs, :unions

  def build
    walk_definitions(@top)
    self
  end

  private

  def walk_definitions(node)
    node.definitions.each { |definition| visit(definition) }
    node.namespaces.each { |namespace| walk_definitions(namespace) }
  end

  def visit(definition)
    if definition.respond_to?(:nested_definitions)
      definition.nested_definitions.each { |nested| visit(nested) }
    end

    case definition
    when Definitions::Enum then @enums << describe_enum(definition)
    when Definitions::Struct then @structs << describe_struct(definition)
    when Definitions::Union then @unions << describe_union(definition)
    end
  end

  def describe_enum(enum)
    identifiers = enum.members.map(&:name)
    {
      'xdr_name' => enum.name,
      'kmp_name' => kmp_name(enum),
      'members' => enum.members.map do |member|
        {
          'identifier' => member.name,
          'value' => member.value,
          'json' => Sep51Names.enum_member_json_name(member.name, identifiers)
        }
      end
    }
  end

  def describe_struct(struct)
    {
      'xdr_name' => struct.name,
      'kmp_name' => kmp_name(struct),
      'fields' => struct.members.map do |member|
        {
          'identifier' => member.name,
          'json' => Sep51Names.struct_field_json_name(member.name)
        }
      end
    }
  end

  # A union arm's key is the discriminant member's own JSON name, so an
  # enum-discriminated arm can never disagree with the enum's own rendering. An
  # integer-discriminated union keys its arms "v" followed by the case value.
  def describe_union(union)
    discriminant_enum = union.discriminant_type
    identifiers = discriminant_enum.is_a?(Definitions::Enum) ? discriminant_enum.members.map(&:name) : nil

    arms = union.arms.flat_map do |arm|
      if arm.is_a?(Definitions::UnionDefaultArm)
        [{ 'case' => 'default', 'json' => nil, 'void' => arm.void? }]
      else
        arm.cases.map do |kase|
          { 'case' => kase.value_s, 'json' => Sep51Names.union_arm_json_key(kase, union), 'void' => arm.void? }
        end
      end
    end

    {
      'xdr_name' => union.name,
      'kmp_name' => kmp_name(union),
      'discriminant' => union.discriminant.name,
      'discriminant_kind' => identifiers ? 'enum' : 'int',
      'arms' => arms
    }
  end

  def kmp_name(definition) = "#{base_name(definition)}Xdr"

  def base_name(definition)
    parent = base_name(definition.parent_defn) if definition.is_a?(Concerns::NestedDefinition)
    "#{parent}#{definition.name.camelize}"
  end
end

# Drives the pinned reference CLI.
class Oracle
  UnknownType = Class.new(StandardError)

  def initialize(pin)
    @cli = ENV.fetch('STELLAR_XDR', 'stellar-xdr')
    @pin = pin
    verify_pin
  end

  def types
    @types ||= run!('types', 'list').lines.map(&:strip).reject(&:empty?).to_set
  end

  def knows?(type) = types.include?(type)

  # Renders one enum member. XDR encodes an enum as a four-byte big-endian
  # signed integer, so a member is probed by decoding its own value.
  def decode_enum_member(type, value)
    encoded = Base64.strict_encode64([value].pack('l>'))
    output, status = run('decode', '--type', type, '--input', 'single-base64', '--output', 'json',
                         stdin: encoded)
    raise UnknownType, output.strip unless status.success?

    JSON.parse(output.strip, quirks_mode: true)
  end

  # The reference CLI orders schema properties alphabetically, so a schema is
  # evidence about field names only and says nothing about emission order.
  def struct_schema(type)
    output, status = run('types', 'schema', '--type', type)
    raise UnknownType, output.strip unless status.success?

    JSON.parse(output)
  end

  def self.field_names(schema)
    (schema['properties'] || {}).keys.reject { |key| key == '$schema' }.to_set
  end

  private

  def verify_pin
    output, status = run('version')
    unless status.success?
      raise PrerequisiteError, <<~MESSAGE
        '#{@cli} version' failed; is it the XDR reference CLI?
          #{output.strip}
        Install the pinned build with:
          #{@pin['install']}
      MESSAGE
    end

    version = output.lines.first.to_s.split[1]
    xdr_commit = output.lines.find { |line| line.start_with?('xdr:') }.to_s.split[1]

    return if version == @pin['version'] && xdr_commit == @pin['xdr_commit']

    raise PrerequisiteError, <<~MESSAGE
      reference CLI does not match oracle-pin.json.
          want: version #{@pin['version']}, xdr #{@pin['xdr_commit']}
          got:  version #{version}, xdr #{xdr_commit}
        Install the pinned build with:
          #{@pin['install']}
    MESSAGE
  end

  def run(*args, stdin: nil)
    output, error, status = Open3.capture3(@cli, *args, stdin_data: stdin.to_s)
    [status.success? ? output : "#{output}#{error}", status]
  rescue SystemCallError => e
    # The binary is absent or not executable, which Open3 reports by raising
    # rather than through an exit status.
    raise PrerequisiteError, <<~MESSAGE
      reference CLI #{@cli.inspect} could not be run: #{e.message}
      Install the pinned build with:
        #{@pin['install']}
      then ensure it is on PATH, or set STELLAR_XDR.
    MESSAGE
  end

  def run!(*args)
    output, status = run(*args)
    raise "#{@cli} #{args.join(' ')} failed: #{output}" unless status.success?

    output
  end
end

def load_ast
  sources = Dir.glob(File.join(XDR_DIR, '*.x')).sort.reject do |path|
    EXCLUDED_SOURCES.include?(File.basename(path))
  end

  if sources.empty?
    abort "No .x files in #{XDR_DIR}. Run 'make -C tools/xdrgen-kt download' first."
  end

  Xdrgen::Compilation.new(sources, output_dir: Dir.tmpdir, namespace: 'name_map').ast
end

# The reference CLI spells type names in UpperCamelCase of the .x identifier
# (SCVal becomes ScVal). Nested types carry their parent's name, matching how
# this SDK names them.
def resolve_oracle_type(oracle, kmp_name)
  base = kmp_name.delete_suffix('Xdr')
  candidate = Sep51Names.upper_camel(base)
  return candidate if oracle.knows?(candidate)

  oracle.types.find { |type| type.casecmp?(candidate) || type.casecmp?(base) }
end

def build_type_map(oracle, entries)
  mapping = {}
  unknown = []

  entries.each do |entry|
    resolved = resolve_oracle_type(oracle, entry['kmp_name'])
    if resolved
      mapping[entry['kmp_name']] = resolved
    else
      unknown << entry['kmp_name']
    end
  end

  [mapping, unknown]
end

def diff(oracle, builder, quiet:)
  enum_total = 0
  enum_checked = 0
  enum_unresolvable = []
  struct_checked = 0
  struct_unresolvable = []
  mismatches = []

  builder.enums.each do |entry|
    type = resolve_oracle_type(oracle, entry['kmp_name'])
    entry['members'].each do |member|
      enum_total += 1
      label = "#{entry['xdr_name']}.#{member['identifier']}"

      if type.nil?
        enum_unresolvable << label
        next
      end

      begin
        actual = oracle.decode_enum_member(type, member['value'])
      rescue Oracle::UnknownType
        enum_unresolvable << label
        next
      end

      enum_checked += 1
      next if actual == member['json']

      mismatches << "enum  #{label}: derived #{member['json'].inspect}, reference #{actual.inspect}"
    end
  end

  string_rendered = []

  builder.structs.each do |entry|
    type = resolve_oracle_type(oracle, entry['kmp_name'])
    if type.nil?
      struct_unresolvable << entry['xdr_name']
      next
    end

    begin
      schema = oracle.struct_schema(type)
    rescue Oracle::UnknownType
      struct_unresolvable << entry['xdr_name']
      next
    end

    # A struct the reference renders as a string has no field names to compare.
    if schema['type'] != 'object'
      string_rendered << entry['xdr_name']
      unless STRING_RENDERED_STRUCTS.include?(entry['xdr_name'])
        mismatches << "struct #{entry['xdr_name']}: reference renders it as " \
                      "#{schema['type'].inspect}, not an object, and it is not a known " \
                      'string-rendered type'
      end
      next
    end

    struct_checked += 1
    derived = entry['fields'].map { |field| field['json'] }.to_set
    actual = Oracle.field_names(schema)
    next if derived == actual

    mismatches << "struct #{entry['xdr_name']}: derived #{derived.to_a.sort.inspect}, " \
                  "reference #{actual.to_a.sort.inspect}"
  end

  # A type the reference cannot resolve says nothing about its rendering, and it is already
  # reported as unresolvable, so it is not also counted as a lost string rendering.
  (STRING_RENDERED_STRUCTS - string_rendered - struct_unresolvable).each do |name|
    mismatches << "struct #{name}: expected the reference to render it as a string, but it " \
                  'did not; its string rendering may have been dropped'
  end

  enum_mismatches = mismatches.count { |line| line.start_with?('enum ') }
  struct_mismatches = mismatches.count { |line| line.start_with?('struct ') }

  unless quiet
    puts
    puts 'Unresolvable by the pinned reference CLI (newer than the commit it vendors):'
    puts "  enum members (#{enum_unresolvable.length}):"
    enum_unresolvable.each { |label| puts "    #{label}" }
    puts "  struct types (#{struct_unresolvable.length}):"
    struct_unresolvable.each { |label| puts "    #{label}" }
    puts
    puts "Rendered as a string by the reference, so no field names to diff (#{string_rendered.length}):"
    string_rendered.sort.each { |label| puts "    #{label}" }
  end

  puts
  puts 'SEP-0051 name derivation vs pinned reference CLI'
  puts format('  enum members:  %d total, %d resolvable, %d matched, %d mismatched',
              enum_total, enum_checked, enum_checked - enum_mismatches, enum_mismatches)
  puts format('  struct types:  %d total, %d field-comparable, %d matched, %d mismatched ' \
              '(%d string-rendered, %d unresolvable)',
              builder.structs.length, struct_checked, struct_checked - struct_mismatches,
              struct_mismatches, string_rendered.length, struct_unresolvable.length)

  unless mismatches.empty?
    puts
    puts 'Mismatches:'
    mismatches.each { |line| puts "  #{line}" }
  end

  { enum_total: enum_total, enum_checked: enum_checked, enum_unresolvable: enum_unresolvable,
    struct_checked: struct_checked, struct_unresolvable: struct_unresolvable,
    string_rendered: string_rendered, mismatches: mismatches }
end

# Writes the artefact, or in check mode compares it with what is already committed and reports
# whether it is current. Returns true when the file on disk matches the freshly derived content.
def emit(name, content, check_only:)
  path = File.join(SCRIPT_DIR, name)

  unless check_only
    File.write(path, content)
    puts "Wrote #{path}"
    return true
  end

  committed = File.exist?(path) ? File.read(path) : nil
  if committed == content
    puts "Up to date: #{path}"
    true
  else
    puts "STALE: #{path} differs from the freshly derived table; re-run without --check"
    false
  end
end

def main
  check_only = ARGV.include?('--check')
  # Checking without diffing would compare the artefacts against themselves and prove nothing.
  run_diff = ARGV.include?('--diff') || check_only
  quiet = ARGV.include?('--quiet')

  builder = NameMapBuilder.new(load_ast).build

  pin = JSON.parse(File.read(PIN_FILE))
  oracle = Oracle.new(pin)

  entries = builder.enums + builder.structs + builder.unions
  type_map, unknown = build_type_map(oracle, entries)

  type_map_content =
    "#{JSON.pretty_generate(
      'oracle' => pin.slice('tool', 'version', 'xdr_commit'),
      'sdk_xdr_commit' => pin['sdk_xdr_commit'],
      'description' => 'Maps this SDK\'s generated XDR type names to the reference CLI\'s ' \
                       'spelling. unknown_to_oracle lists every type the reference build ' \
                       'does not resolve, whatever the cause; the expected cause is a type ' \
                       'added after the XDR commit that build vendors, and the list is ' \
                       'empty while the two pins agree closely enough.',
      'type_map' => type_map.sort.to_h,
      'unknown_to_oracle' => unknown.sort
    )}\n"

  result = run_diff ? diff(oracle, builder, quiet: quiet) : nil

  name_map_content =
    "#{JSON.pretty_generate(
      'oracle' => pin.slice('tool', 'version', 'xdr_commit'),
      'sdk_xdr_commit' => pin['sdk_xdr_commit'],
      'description' => 'SEP-0051 XDR-JSON names derived from the .x sources. Regenerate with ' \
                       'ruby tools/sep-51-oracle/name_map.rb --diff after any XDR pin bump.',
      'verification' => result ? {
        'enum_members_total' => result[:enum_total],
        'enum_members_checked' => result[:enum_checked],
        'enum_members_unresolvable' => result[:enum_unresolvable].sort,
        'struct_types_total' => builder.structs.length,
        'struct_types_field_comparable' => result[:struct_checked],
        'struct_types_string_rendered' => result[:string_rendered].sort,
        'struct_types_unresolvable' => result[:struct_unresolvable].sort,
        'mismatches' => result[:mismatches]
      } : 'not verified in this run; re-run with --diff',
      'enums' => builder.enums,
      'structs' => builder.structs,
      'unions' => builder.unions
    )}\n"

  puts
  current = emit('type_map.json', type_map_content, check_only: check_only)
  current &= emit('name-map.json', name_map_content, check_only: check_only)

  exit 1 if result && !result[:mismatches].empty?
  exit 1 unless current
end

if $PROGRAM_NAME == __FILE__
  begin
    main
  rescue PrerequisiteError => e
    warn "name_map.rb: #{e.message}"
    exit 2
  end
end
